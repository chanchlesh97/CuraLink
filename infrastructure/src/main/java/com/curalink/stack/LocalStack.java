package com.curalink.stack;

import org.apache.commons.logging.Log;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.elasticache.CfnCacheCluster;
import software.amazon.awscdk.services.elasticache.CfnSubnetGroup;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.CfnHealthCheck;
import software.amazon.awscdk.services.servicediscovery.DnsRecordType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LocalStack extends Stack {
    private final Vpc vpc;
    private final Cluster ecsCluster;
    private final CfnCacheCluster elasticCacheCluster;
    public LocalStack(final App scope, final String id, final StackProps props) {
        super(scope, id, props);
        this.vpc = createVpc();

        DatabaseInstance authServiceDb = createDatabaseInstance("AuthServiceDB", "auth-service-db");
        DatabaseInstance patientServiceDb = createDatabaseInstance("PatientServiceDB", "patient-service-db");

        CfnHealthCheck authDbHealthCheck = createDBHealthCheck(authServiceDb, "AuthServiceDBHealthCheck");
        CfnHealthCheck patientDbHealthCheck = createDBHealthCheck(patientServiceDb, "PatientServiceDBHealthCheck");

        CfnCluster mskCluster = createMskCluster();
        this.elasticCacheCluster = createRedisCluster();
        this.ecsCluster = createEcsCluster();

        FargateService authService = createFargateService(
                "AuthService",
                "auth-service",
                List.of(4005),
                authServiceDb,
                Map.of("JWT_SECRET", "QNEhk+k8hr58a15BuVWXZQjNsQiQUB+5DjwDDj5lpSA="),
                "auth-service-db"
        );

        authService.getNode().addDependency(authDbHealthCheck);
        authService.getNode().addDependency(authServiceDb);

        FargateService billingService = createFargateService(
                "BillingService",
                "billing-service",
                List.of(4001, 9001),
                null,
                null,
                null
        );

        FargateService analyticsService = createFargateService(
                "AnalyticsService",
                "analytics-service",
                List.of(4002),
                null,
                null,
                null
        );

        analyticsService.getNode().addDependency(mskCluster);
        FargateService patientService = createFargateService(
                "PatientService",
                "patient-service",
                List.of(4000),
                patientServiceDb,
                Map.of(
                        "BILLING_SERVICE_ADDRESS", "billing-service.cura-link.local",
                        "BILLING_SERVICE_GRPC_PORT", "9001"
                ),
                "patient-service-db"
        );

        patientService.getNode().addDependency(patientServiceDb);
        patientService.getNode().addDependency(patientDbHealthCheck);
        patientService.getNode().addDependency(billingService);
        patientService.getNode().addDependency(mskCluster);
        patientService.getNode().addDependency(elasticCacheCluster);


        ApplicationLoadBalancedFargateService apiGatewayService = createApiGatewayService();
        apiGatewayService.getNode().addDependency(elasticCacheCluster);
//        this.createApiGatewayService();

        FargateService prometheusService = createFargateService(
                "PrometheusService",
                "prometheus-prod",
                List.of(9090),
                null,
                null,
                null
        );

        createGrafanaService();

    }


    private Vpc createVpc() {
        return Vpc.Builder.create(this, "PatientManagementVPC")
                .vpcName("PatientManagementVPC")
                .maxAzs(2)
                .build();
    }

    private DatabaseInstance createDatabaseInstance(String id, String dbName ) {
        return DatabaseInstance.Builder
                .create(this, id)
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder()
                                .version(PostgresEngineVersion.VER_17_2)
                                .build()
                ))
                .vpc(this.vpc)
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO))
                .allocatedStorage(20)
                .credentials(Credentials.fromGeneratedSecret("admin_user"))
                .databaseName(dbName)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
    }

    private CfnHealthCheck createDBHealthCheck(DatabaseInstance dbInstance, String id) {
        return CfnHealthCheck.Builder.create(this, id)
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP")
                        .port(Token.asNumber(dbInstance.getDbInstanceEndpointPort()))
                        .ipAddress(dbInstance.getDbInstanceEndpointAddress())
                        .requestInterval(30)
                        .failureThreshold(3)
                        .build())
                .build();
    }

    private CfnCluster createMskCluster() {
        return CfnCluster.Builder.create(this, "MskCluster")
                .clusterName("kafka-cluster")
                .kafkaVersion("3.8.x")
                .numberOfBrokerNodes(2)
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                    .instanceType("kafka.m5.xlarge")
                    .clientSubnets(this.vpc.getPrivateSubnets().stream().map(ISubnet::getSubnetId).collect(Collectors.toList()))
                    .brokerAzDistribution("DEFAULT")
                    .build())
                .build();
    }

    // auth-service.patient-management.local for finding services via Cloud Map
    private Cluster createEcsCluster() {
        return Cluster.Builder.create(this, "PatientManagementCluster")
                .vpc(this.vpc)
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                        .name("cura-link.local")
                        .build())
                .build();
    }

    private FargateService createFargateService(String id, String imageName, List<Integer> ports, DatabaseInstance dbInstance, Map<String, String> additionalEvnVars, String dbName) {
        FargateTaskDefinition fargateTaskDefinition = FargateTaskDefinition.Builder.create(this, id + "Task")
                .cpu(256)
                .memoryLimitMiB(512)
                .build();

        ContainerDefinitionOptions.Builder containerDefinitionOptions= ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry(imageName))
                .portMappings(ports.stream()
                        .map(port -> PortMapping.builder()
                                .containerPort(port)
                                .hostPort(port)
                                .protocol(Protocol.TCP)
                                .build())
                        .toList())
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(LogGroup.Builder.create(this, id+"LogGroup")
                                .logGroupName("/ecs"+imageName)
                                .removalPolicy(RemovalPolicy.DESTROY)
                                .retention(RetentionDays.ONE_DAY)
                                .build())
                                .streamPrefix(imageName)
                        .build()));
//                .build();

        Map<String, String> envVars = new HashMap<>();
        envVars.put("SPRING_KAFKA_BOOTSTRAP_SERVERS", "localhost.localstack.cloud:4510, localhost.localstack.cloud:4511, localhost.localstack.cloud:4512");
        envVars.put("SPRING_CACHE_TYPE", "redis");
//        envVars.put
        envVars.put("SPRING_DATA_REDIS_HOST", elasticCacheCluster.getAttrRedisEndpointAddress());
        envVars.put("SPRING_DATA_REDIS_PORT", elasticCacheCluster.getAttrRedisEndpointPort());

        if(additionalEvnVars != null) {
            envVars.putAll(additionalEvnVars);
        }

        if(dbInstance != null) {
            envVars.put("SPRING_DATASOURCE_URL", String.format("jdbc:postgresql://%s:%s/%s",
                    dbInstance.getDbInstanceEndpointAddress(),
                    dbInstance.getDbInstanceEndpointPort(),
                    dbName));
            envVars.put("SPRING_DATASOURCE_USERNAME", "admin_user");
            // In a real-world scenario, you would retrieve the password securely
            envVars.put("SPRING_DATASOURCE_PASSWORD", dbInstance.getSecret().secretValueFromJson("password").toString());
            envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
            envVars.put("SPRING_SQL_INIT_MODE", "always");
            envVars.put("SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT", "60000");
        }

        containerDefinitionOptions.environment(envVars);
        fargateTaskDefinition.addContainer(imageName + "Container", containerDefinitionOptions.build());

        return FargateService.Builder.create(this, id)
                .cluster(this.ecsCluster)
                .taskDefinition(fargateTaskDefinition)
                .assignPublicIp(false)
                .cloudMapOptions(CloudMapOptions.builder()
                        .name(imageName)
                        .dnsRecordType(DnsRecordType.A)
                        .build())
                .serviceName(imageName)
                .build();

    }

    private ApplicationLoadBalancedFargateService createApiGatewayService() {
        FargateTaskDefinition fargateTaskDefinition = FargateTaskDefinition.Builder.create(this, "ApiGatewayTask")
                .cpu(256)
                .memoryLimitMiB(512)
                .build();

        ContainerDefinitionOptions containerDefinitionOptions= ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("api-gateway"))
                .environment(Map.of(
                        "SPRING_PROFILES_ACTIVE", "prod",
                        "AUTH_SERVICE_URL", "http://auth-service.cura-link.local:4005",
                        "REDIS_HOST", elasticCacheCluster.getAttrRedisEndpointAddress(),
                        "REDIS_PORT", elasticCacheCluster.getAttrRedisEndpointPort()
                ))
                .portMappings(List.of(4004).stream()
                        .map(port -> PortMapping.builder()
                                .containerPort(port)
                                .hostPort(port)
                                .protocol(Protocol.TCP)
                                .build())
                        .toList())
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(LogGroup.Builder.create(this, "ApiGatewayLogGroup")
                                .logGroupName("/ecs/api-gateway")
                                .removalPolicy(RemovalPolicy.DESTROY)
                                .retention(RetentionDays.ONE_DAY)
                                .build())
                        .streamPrefix("api-gateway")
                        .build()))
                .build();

        fargateTaskDefinition.addContainer("ApiGatewayContainer", containerDefinitionOptions);

        ApplicationLoadBalancedFargateService apiGatewayService = ApplicationLoadBalancedFargateService.Builder.create(this, "ApiGatewayService")
                .cluster(this.ecsCluster)
                .taskDefinition(fargateTaskDefinition)
                .serviceName("api-gateway")
                .listenerPort(4004)
                .desiredCount(1)
                .healthCheckGracePeriod(Duration.seconds(60))
                .publicLoadBalancer(true)
                .cloudMapOptions(CloudMapOptions.builder()
                        .name("api-gateway")
                        .dnsRecordType(DnsRecordType.A)
                        .build())
                .build();
        return apiGatewayService;
    }

    private CfnCacheCluster createRedisCluster() {

        CfnSubnetGroup redisSubnetGroup = CfnSubnetGroup.Builder
                .create(this, "RedisSubnetGroup")
                .description("Redis/ElasticCache Subnet Group")
                .subnetIds(
                        vpc.getPrivateSubnets().stream()
                                .map(ISubnet::getSubnetId)
                                .collect(Collectors.toList())
                )
                .build();

        return CfnCacheCluster.Builder.create(this, "RedisCluster")
                .cacheNodeType("cache.t3.micro")
                .engine("redis")
                .numCacheNodes(1)
                .clusterName("redis-cluster")
                .vpcSecurityGroupIds(List.of(this.vpc.getVpcDefaultSecurityGroup()))
                .cacheSubnetGroupName(redisSubnetGroup.getCacheSubnetGroupName())
                .build();
    }

    private ApplicationLoadBalancedFargateService createGrafanaService() {
        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder
                .create(this, "GrafanaService")
                .cpu(256)
                .memoryLimitMiB(512)
                .build();
        taskDefinition.addContainer("GrafanaContainer", ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromRegistry("grafana/grafana"))
                        .portMappings(List.of(PortMapping.builder()
                                .containerPort(3000)
                                .build()))
                .build());

        ApplicationLoadBalancedFargateService service = ApplicationLoadBalancedFargateService.Builder
                .create(this, "GrafanaUIService")
                .taskDefinition(taskDefinition)
                .publicLoadBalancer(true)
                .listenerPort(3000)
                .desiredCount(1)
                .build();
        return service;
    }
    public static void main(final String[] args) {
        App app = new App(AppProps.builder().outdir("./cdk.out").build());

        StackProps props = StackProps.builder()
                .synthesizer(new BootstraplessSynthesizer())
                .build();
        new LocalStack(app, "localstack", props);
        app.synth();
        System.out.println("App Synthesizer in progress...");
    }
}