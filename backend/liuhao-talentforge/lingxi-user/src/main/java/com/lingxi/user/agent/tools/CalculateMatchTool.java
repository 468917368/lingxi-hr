package com.lingxi.user.agent.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.domain.Result;
import com.lingxi.common.util.RedisUtil;
import com.lingxi.user.feign.JobFeignClient;
import com.lingxi.user.feign.ResumeFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 计算匹配度工具（Agent 内部计算，不调外部匹配接口）
 *
 * 匹配逻辑（5维度，支持语义匹配）：
 * ① 硬技能匹配（权重 35%）：岗位要求的技能 vs 用户掌握的技能（支持同义词）
 * ② 软技能匹配（权重 15%）：从JD和简历项目描述中提取能力，语义匹配
 * ③ 经验匹配（权重 20%）：岗位要求年限 vs 用户工作年限
 * ④ 学历匹配（权重 10%）：岗位要求学历 vs 用户学历
 * ⑤ 岗位匹配（权重 20%）：用户期望岗位 vs 岗位标题（关键词匹配）
 *
 * @author 成员A
 * @since 2026-08-05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalculateMatchTool {

    private final JobFeignClient jobFeignClient;
    private final ResumeFeignClient resumeFeignClient;
    private final ObjectMapper objectMapper;
    private final RedisUtil redisUtil;

    private static final String CACHE_KEY_PREFIX = "agent:match:";
    private static final long CACHE_TTL_MINUTES = 10;

    /** 学历等级映射 */
    private static final Map<String, Integer> EDUCATION_LEVEL = new LinkedHashMap<>();
    static {
        EDUCATION_LEVEL.put("高中", 1);
        EDUCATION_LEVEL.put("大专", 2);
        EDUCATION_LEVEL.put("本科", 3);
        EDUCATION_LEVEL.put("硕士", 4);
        EDUCATION_LEVEL.put("博士", 5);
        // 英文枚举
        EDUCATION_LEVEL.put("HIGH_SCHOOL", 1);
        EDUCATION_LEVEL.put("COLLEGE", 2);
        EDUCATION_LEVEL.put("BACHELOR", 3);
        EDUCATION_LEVEL.put("MASTER", 4);
        EDUCATION_LEVEL.put("PHD", 5);
    }

    /** 权重常量（6维度） */
    private static final double SKILL_WEIGHT = 0.30;      // 技能匹配 30%
    private static final double EXPERIENCE_WEIGHT = 0.20;  // 经验匹配 20%
    private static final double EDUCATION_WEIGHT = 0.10;   // 学历匹配 10%
    private static final double JOB_TITLE_WEIGHT = 0.25;   // 岗位匹配 25%（核心维度）
    private static final double SALARY_WEIGHT = 0.05;      // 薪资匹配 5%
    private static final double CITY_WEIGHT = 0.10;        // 城市匹配 10%

    /** 城市等级映射（用于城市匹配） */
    private static final Map<String, Integer> CITY_LEVEL = new HashMap<>();
    static {
        // 一线城市
        CITY_LEVEL.put("北京", 1);
        CITY_LEVEL.put("上海", 1);
        CITY_LEVEL.put("广州", 1);
        CITY_LEVEL.put("深圳", 1);
        // 新一线城市
        CITY_LEVEL.put("杭州", 2);
        CITY_LEVEL.put("成都", 2);
        CITY_LEVEL.put("武汉", 2);
        CITY_LEVEL.put("南京", 2);
        CITY_LEVEL.put("重庆", 2);
        CITY_LEVEL.put("西安", 2);
        CITY_LEVEL.put("苏州", 2);
        CITY_LEVEL.put("天津", 2);
        CITY_LEVEL.put("长沙", 2);
        CITY_LEVEL.put("郑州", 2);
        // 二线城市
        CITY_LEVEL.put("合肥", 3);
        CITY_LEVEL.put("青岛", 3);
        CITY_LEVEL.put("大连", 3);
        CITY_LEVEL.put("厦门", 3);
        CITY_LEVEL.put("福州", 3);
        CITY_LEVEL.put("济南", 3);
        CITY_LEVEL.put("昆明", 3);
        CITY_LEVEL.put("贵阳", 3);
        CITY_LEVEL.put("南昌", 3);
        CITY_LEVEL.put("石家庄", 3);
    }

    /** 能力关键词映射（语义匹配用） */
    private static final Map<String, List<String>> ABILITY_KEYWORDS = new LinkedHashMap<>();
    static {
        // 高并发/性能相关
        ABILITY_KEYWORDS.put("高并发", Arrays.asList(
            "高并发", "高qps", "高吞吐", "大流量", "秒杀", "抢购", "限流", "降级", "熔断",
            "负载均衡", "水平扩展", "弹性伸缩", "性能优化", "压测", "jvm调优", "gc调优"
        ));

        // 分布式/微服务相关
        ABILITY_KEYWORDS.put("分布式", Arrays.asList(
            "分布式", "微服务", "集群", "服务治理", "服务注册", "服务发现", "配置中心",
            "链路追踪", "分布式事务", "分布式锁", "分布式缓存", "cap理论", "最终一致性"
        ));

        // 系统设计/架构相关
        ABILITY_KEYWORDS.put("系统设计", Arrays.asList(
            "系统设计", "架构设计", "技术方案", "概要设计", "详细设计", "领域驱动", "ddd",
            "设计模式", "ddd", "clean architecture", "六边形架构", "事件驱动", "cqrs"
        ));

        // 数据处理相关
        ABILITY_KEYWORDS.put("数据处理", Arrays.asList(
            "大数据", "数据处理", "etl", "数据仓库", "数据清洗", "数据迁移", "数据同步",
            "实时计算", "流计算", "批处理", "数据建模", "数据治理", "数据质量"
        ));

        // 项目管理相关
        ABILITY_KEYWORDS.put("项目管理", Arrays.asList(
            "项目管理", "需求分析", "需求评审", "技术评审", "代码评审", "code review",
            "敏捷开发", "scrum", "迭代管理", "风险管理", "进度管理", "质量管理"
        ));

        // 团队协作相关
        ABILITY_KEYWORDS.put("团队协作", Arrays.asList(
            "团队协作", "跨部门", "沟通能力", "协调能力", "leadership", "带团队",
            "技术分享", "培训", "指导", "mentor", "技术氛围", "团队建设"
        ));

        // 问题解决相关
        ABILITY_KEYWORDS.put("问题解决", Arrays.asList(
            "问题定位", "故障排查", "根因分析", "性能调优", "线上问题", "应急响应",
            "故障恢复", "应急预案", "监控告警", "日志分析", "链路分析"
        ));

        // 学习能力相关
        ABILITY_KEYWORDS.put("学习能力", Arrays.asList(
            "学习能力", "技术热情", "自驱力", "主动性", "好奇心", "技术视野",
            "新技术", "技术趋势", "持续学习", "成长", "进阶"
        ));
    }

    /** 软技能关键词（从JD提取） */
    private static final String[] SOFT_SKILL_KEYWORDS = {
        "沟通", "协作", "团队", "领导", "责任心", "抗压", "自驱", "主动",
        "学习", "热情", "细心", "耐心", "逻辑", "分析", "解决问题"
    };

    /** 技能同义词组（核心词 → 同义词列表） */
    private static final Map<String, List<String>> SKILL_SYNONYMS = new HashMap<>();
    static {
        // 编程语言
        SKILL_SYNONYMS.put("java", Arrays.asList("java", "jdk", "j2ee", "javase", "javaee", "jvm", "spring", "spring boot", "springboot", "spring mvc", "springmvc", "ssm", "spring cloud", "springcloud", "mybatis", "mybatis-plus", "hibernate"));
        SKILL_SYNONYMS.put("python", Arrays.asList("python", "py", "python3", "cpython", "django", "flask", "fastapi", "pandas", "numpy"));
        SKILL_SYNONYMS.put("javascript", Arrays.asList("javascript", "js", "es6", "es2015", "ecmascript", "node", "nodejs", "node.js", "react", "vue", "angular", "typescript", "ts"));
        SKILL_SYNONYMS.put("typescript", Arrays.asList("typescript", "ts"));
        SKILL_SYNONYMS.put("go", Arrays.asList("go", "golang", "gin", "beego", "echo"));
        SKILL_SYNONYMS.put("c++", Arrays.asList("c++", "cpp", "c plus plus", "stl", "boost"));
        SKILL_SYNONYMS.put("c#", Arrays.asList("c#", "csharp", ".net", "dotnet", "asp.net"));
        SKILL_SYNONYMS.put("php", Arrays.asList("php", "php7", "php8", "laravel", "thinkphp", "yii"));
        SKILL_SYNONYMS.put("rust", Arrays.asList("rust", "rustlang", "cargo"));
        SKILL_SYNONYMS.put("kotlin", Arrays.asList("kotlin", "kt", "kotlin android"));
        SKILL_SYNONYMS.put("swift", Arrays.asList("swift", "swiftui", "ios开发"));

        // 框架
        SKILL_SYNONYMS.put("spring", Arrays.asList("spring", "spring boot", "springboot", "spring mvc", "springmvc", "ssm", "spring cloud", "springcloud", "spring framework"));
        SKILL_SYNONYMS.put("react", Arrays.asList("react", "react.js", "reactjs", "react native", "reactnative", "nextjs", "next.js"));
        SKILL_SYNONYMS.put("vue", Arrays.asList("vue", "vue.js", "vuejs", "vue2", "vue3", "nuxt", "nuxtjs"));
        SKILL_SYNONYMS.put("angular", Arrays.asList("angular", "angular.js", "angularjs", "angular2", "angular4", "angular6"));
        SKILL_SYNONYMS.put("django", Arrays.asList("django", "django rest framework", "drf"));
        SKILL_SYNONYMS.put("flask", Arrays.asList("flask", "flask-restful"));
        SKILL_SYNONYMS.put("express", Arrays.asList("express", "express.js", "expressjs"));
        SKILL_SYNONYMS.put("mybatis", Arrays.asList("mybatis", "mybatis-plus", "mybatis plus", "mybatisplus", "mybatis-plus-boot-starter"));

        // 数据库
        SKILL_SYNONYMS.put("mysql", Arrays.asList("mysql", "mariadb", "mysql8", "mysql5"));
        SKILL_SYNONYMS.put("postgresql", Arrays.asList("postgresql", "postgres", "pg"));
        SKILL_SYNONYMS.put("mongodb", Arrays.asList("mongodb", "mongo", "mongoose"));
        SKILL_SYNONYMS.put("redis", Arrays.asList("redis", "jedis", "lettuce", "redisson", "redis cluster"));
        SKILL_SYNONYMS.put("elasticsearch", Arrays.asList("elasticsearch", "es", "elastic", "elk", "kibana", "logstash"));

        // 中间件
        SKILL_SYNONYMS.put("kafka", Arrays.asList("kafka", "apache kafka", "kafka stream"));
        SKILL_SYNONYMS.put("rabbitmq", Arrays.asList("rabbitmq", "rabbit mq", "amqp"));
        SKILL_SYNONYMS.put("rocketmq", Arrays.asList("rocketmq", "rocket mq", "apache rocketmq"));
        SKILL_SYNONYMS.put("zookeeper", Arrays.asList("zookeeper", "zk", "apache zookeeper"));
        SKILL_SYNONYMS.put("nginx", Arrays.asList("nginx", "反向代理", "负载均衡"));

        // 容器/DevOps
        SKILL_SYNONYMS.put("docker", Arrays.asList("docker", "dockerfile", "container", "容器化", "docker compose"));
        SKILL_SYNONYMS.put("kubernetes", Arrays.asList("kubernetes", "k8s", "kubectl", "helm", "pod", "deployment"));
        SKILL_SYNONYMS.put("jenkins", Arrays.asList("jenkins", "ci/cd", "cicd", "持续集成", "持续部署"));
        SKILL_SYNONYMS.put("linux", Arrays.asList("linux", "centos", "ubuntu", "debian", "shell", "bash", "unix"));
        SKILL_SYNONYMS.put("git", Arrays.asList("git", "github", "gitlab", "svn", "版本控制"));

        // 云服务
        SKILL_SYNONYMS.put("aws", Arrays.asList("aws", "amazon web services", "ec2", "s3", "lambda", "rds"));
        SKILL_SYNONYMS.put("阿里云", Arrays.asList("阿里云", "aliyun", "alicloud", "ecs", "oss", "rds", "slb"));
        SKILL_SYNONYMS.put("腾讯云", Arrays.asList("腾讯云", "tencent cloud", "qcloud", "cos"));

        // 大数据
        SKILL_SYNONYMS.put("hadoop", Arrays.asList("hadoop", "hdfs", "mapreduce", "yarn"));
        SKILL_SYNONYMS.put("spark", Arrays.asList("spark", "apache spark", "spark streaming", "spark sql"));
        SKILL_SYNONYMS.put("flink", Arrays.asList("flink", "apache flink", "flink streaming", "flink sql"));
        SKILL_SYNONYMS.put("hive", Arrays.asList("hive", "apache hive", "数据仓库"));

        // 前端
        SKILL_SYNONYMS.put("html", Arrays.asList("html", "html5", "h5"));
        SKILL_SYNONYMS.put("css", Arrays.asList("css", "css3", "scss", "sass", "less", "tailwind"));
        SKILL_SYNONYMS.put("webpack", Arrays.asList("webpack", "vite", "rollup", "parcel", "打包工具", "构建工具"));

        // 移动端
        SKILL_SYNONYMS.put("android", Arrays.asList("android", "安卓", "kotlin android", "java android", "android studio"));
        SKILL_SYNONYMS.put("ios", Arrays.asList("ios", "iphone", "ipad", "objective-c", "objc", "xcode"));
        SKILL_SYNONYMS.put("flutter", Arrays.asList("flutter", "dart", "flutter sdk"));
        SKILL_SYNONYMS.put("react native", Arrays.asList("react native", "reactnative", "rn"));

        // 设计
        SKILL_SYNONYMS.put("figma", Arrays.asList("figma", "sketch", "adobe xd", "xd", "ui设计"));
        SKILL_SYNONYMS.put("photoshop", Arrays.asList("photoshop", "ps", "adobe photoshop", "图像处理"));

        // 测试
        SKILL_SYNONYMS.put("junit", Arrays.asList("junit", "junit5", "junit4", "单元测试", "unit test", "mockito"));
        SKILL_SYNONYMS.put("selenium", Arrays.asList("selenium", "自动化测试", "ui测试", "web测试"));
        SKILL_SYNONYMS.put("jmeter", Arrays.asList("jmeter", "性能测试", "压力测试", "loadrunner", "压测"));

        // 微服务相关
        SKILL_SYNONYMS.put("微服务", Arrays.asList("微服务", "microservice", "spring cloud", "dubbo", "grpc", "服务治理", "服务注册", "服务发现"));
        SKILL_SYNONYMS.put("分布式", Arrays.asList("分布式", "distributed", "集群", "cluster", "一致性", "容错"));
        SKILL_SYNONYMS.put("高并发", Arrays.asList("高并发", "高qps", "高吞吐", "大流量", "秒杀", "限流", "降级", "熔断", "负载均衡"));

        // 数据分析
        SKILL_SYNONYMS.put("数据分析", Arrays.asList("数据分析", "data analysis", "数据挖掘", "数据建模", "bi", "商业智能"));
        SKILL_SYNONYMS.put("机器学习", Arrays.asList("机器学习", "machine learning", "ml", "深度学习", "deep learning", "dl", "ai", "人工智能"));
    }

    /** 岗位方向分类（用于岗位匹配） */
    private static final Map<String, List<String>> JOB_DIRECTION = new HashMap<>();
    static {
        // 后端开发
        JOB_DIRECTION.put("后端开发", Arrays.asList(
            "java", "python", "go", "golang", "c++", "c#", "php", "rust", "后端", "backend", "服务端",
            "开发工程师", "开发", "研发", "工程师"
        ));
        // 前端开发
        JOB_DIRECTION.put("前端开发", Arrays.asList(
            "前端", "frontend", "web前端", "react", "vue", "angular", "javascript", "typescript"
        ));
        // 移动开发
        JOB_DIRECTION.put("移动开发", Arrays.asList(
            "android", "安卓", "ios", "移动端", "手机开发", "app开发", "flutter", "react native"
        ));
        // 数据开发
        JOB_DIRECTION.put("数据开发", Arrays.asList(
            "大数据", "数据开发", "数据仓库", "etl", "hadoop", "spark", "flink", "数据分析", "数据挖掘"
        ));
        // 算法/AI
        JOB_DIRECTION.put("算法/AI", Arrays.asList(
            "算法", "机器学习", "深度学习", "人工智能", "ai", "nlp", "cv", "推荐算法"
        ));
        // 测试
        JOB_DIRECTION.put("测试", Arrays.asList(
            "测试", "qa", "质量", "自动化测试", "性能测试", "测试开发"
        ));
        // 运维/DevOps
        JOB_DIRECTION.put("运维/DevOps", Arrays.asList(
            "运维", "devops", "sre", "运维开发", "k8s", "docker", "linux"
        ));
        // 产品
        JOB_DIRECTION.put("产品", Arrays.asList(
            "产品经理", "产品", "pm", "产品设计"
        ));
        // 设计
        JOB_DIRECTION.put("设计", Arrays.asList(
            "ui", "ue", "交互", "设计", "视觉", "平面", "ui设计"
        ));
    }

    /**
     * 执行匹配度计算
     *
     * @param jobId    岗位ID
     * @param userId   用户ID
     * @return JSON 格式的匹配结果
     */
    public String execute(Long jobId, Long userId) {
        log.info("开始计算匹配度: jobId={}, userId={}", jobId, userId);

        // 检查缓存
        String cacheKey = CACHE_KEY_PREFIX + userId + ":" + jobId;
        try {
            String cached = redisUtil.get(cacheKey);
            if (cached != null) {
                log.info("匹配缓存命中: userId={}, jobId={}", userId, jobId);
                return cached;
            }
        } catch (Exception e) {
            log.warn("读取匹配缓存失败", e);
        }

        try {
            // ① 获取岗位画像（技能要求）
            log.info("获取岗位详情: jobId={}", jobId);
            Result<Map<String, Object>> jobResult = jobFeignClient.getJobDetail(jobId);
            Map<String, Object> jobData = jobResult.getData();
            if (jobData == null || jobData.isEmpty()) {
                log.warn("岗位不存在: jobId={}", jobId);
                return errorJson("岗位不存在");
            }
            log.info("岗位详情获取成功: jobId={}, keys={}", jobId, jobData.keySet());

            // ② 获取用户简历
            log.info("获取用户简历: userId={}", userId);
            Result<Map<String, Object>> resumeResult = resumeFeignClient.getUserResume(userId);
            Map<String, Object> resumeData = resumeResult.getData();
            if (resumeData == null || resumeData.isEmpty() || Boolean.FALSE.equals(resumeData.get("hasResume"))) {
                log.warn("用户暂无简历: userId={}", userId);
                return errorJson("用户暂无简历");
            }
            log.info("用户简历获取成功: userId={}, keys={}", userId, resumeData.keySet());
            // 打印 cardStructure 内容用于调试
            Object cardStructure = resumeData.get("cardStructure");
            if (cardStructure != null) {
                log.info("cardStructure 类型: {}", cardStructure.getClass().getName());
                try {
                    String cardJson = objectMapper.writeValueAsString(cardStructure);
                    log.info("cardStructure 内容(前500字): {}", cardJson.substring(0, Math.min(500, cardJson.length())));
                } catch (Exception e) {
                    log.warn("序列化 cardStructure 失败", e);
                }
            } else {
                log.warn("cardStructure 为 null");
            }

            // ③ 提取岗位要求
            List<String> requiredSkills = extractJobSkills(jobData);
            String jobEducation = (String) jobData.getOrDefault("educationRequirement", "");
            Integer jobExperience = jobData.get("minExperienceYears") != null ?
                    ((Number) jobData.get("minExperienceYears")).intValue() : 0;
            String jobTitle = (String) jobData.getOrDefault("title", "");
            String jdText = (String) jobData.getOrDefault("jdText", "");
            String jobCity = (String) jobData.getOrDefault("cityName", "");
            Integer salaryMin = jobData.get("salaryMinAmount") != null ?
                    ((Number) jobData.get("salaryMinAmount")).intValue() : 0;
            Integer salaryMax = jobData.get("salaryMaxAmount") != null ?
                    ((Number) jobData.get("salaryMaxAmount")).intValue() : 0;
            log.info("岗位要求: title={}, skills={}, education={}, experience={}, city={}, salary={}-{}K",
                    jobTitle, requiredSkills, jobEducation, jobExperience, jobCity, salaryMin, salaryMax);

            // ④ 提取用户信息（从 cardStructure 中提取）
            List<String> userSkills = extractUserSkills(resumeData);
            String userEducation = extractUserEducation(resumeData);
            Integer userYears = extractUserWorkYears(resumeData);
            String desiredJob = extractUserDesiredJob(resumeData);
            String desiredCity = extractUserDesiredCity(resumeData);
            Integer expectedSalaryMin = extractUserExpectedSalaryMin(resumeData);
            Integer expectedSalaryMax = extractUserExpectedSalaryMax(resumeData);
            String projectDesc = extractProjectDescription(resumeData);
            log.info("用户信息: skills={}, education={}, years={}, desiredJob={}, desiredCity={}, expectedSalary={}-{}K",
                    userSkills, userEducation, userYears, desiredJob, desiredCity, expectedSalaryMin, expectedSalaryMax);

            // ⑤ 计算匹配度（6维度，支持语义匹配）
            MatchResult result = calculate(requiredSkills, userSkills, jobEducation, userEducation,
                    jobExperience, userYears, jobTitle, desiredJob, jdText, projectDesc,
                    jobCity, desiredCity, salaryMin, salaryMax, expectedSalaryMin, expectedSalaryMax);
            log.info("匹配度计算完成: jobId={}, matchScore={}", jobId, result.matchScore);

            // ⑥ 返回结果
            String json = objectMapper.writeValueAsString(result.toMap());

            // ⑦ 写入缓存
            try {
                redisUtil.set(cacheKey, json, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("写入匹配缓存失败", e);
            }

            return json;

        } catch (Exception e) {
            log.error("计算匹配度失败: jobId={}, userId={}", jobId, userId, e);
            return errorJson("计算匹配度失败");
        }
    }

    // ==================== 匹配计算核心 ====================

    private MatchResult calculate(List<String> requiredSkills, List<String> userSkills,
                                   String jobEducation, String userEducation,
                                   int jobExperience, int userYears,
                                   String jobTitle, String desiredJob,
                                   String jdText, String projectDesc,
                                   String jobCity, String desiredCity,
                                   int salaryMin, int salaryMax,
                                   int expectedSalaryMin, int expectedSalaryMax) {
        // ① 硬技能匹配（支持同义词）
        Set<String> userSkillSet = userSkills.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String skill : requiredSkills) {
            if (skillMatchesWithSynonyms(skill, userSkillSet)) {
                matched.add(skill);
            } else {
                missing.add(skill);
            }
        }

        double skillScore = requiredSkills.isEmpty() ? 80.0 :
                (matched.size() * 100.0 / requiredSkills.size());

        // 惩罚：缺失关键技能
        if (!missing.isEmpty() && !requiredSkills.isEmpty()) {
            double missingRatio = (double) missing.size() / requiredSkills.size();
            skillScore = skillScore * (1 - missingRatio * 0.5);
        }

        // ② 经验匹配
        boolean experienceMatch = userYears >= jobExperience;
        double experienceScore;
        if (experienceMatch) {
            experienceScore = userYears <= jobExperience + 2 ? 100.0 :
                Math.max(70, 100 - (userYears - jobExperience - 2) * 10);
        } else {
            experienceScore = Math.max(0, (userYears * 100.0 / Math.max(jobExperience, 1)));
        }

        // ③ 学历匹配
        int jobLevel = EDUCATION_LEVEL.getOrDefault(jobEducation, 0);
        int userLevel = EDUCATION_LEVEL.getOrDefault(userEducation, 0);
        boolean educationMatch = userLevel >= jobLevel;
        double educationScore = educationMatch ? 100.0 :
                (jobLevel > 0 ? Math.max(0, (userLevel * 100.0 / jobLevel)) : 80.0);

        // ④ 岗位匹配（期望岗位 vs 岗位标题）
        double jobTitleScore = calculateJobTitleScore(jobTitle, desiredJob);

        // ⑤ 薪资匹配
        double salaryScore = calculateSalaryScore(salaryMin, salaryMax, expectedSalaryMin, expectedSalaryMax);

        // ⑥ 城市匹配
        double cityScore = calculateCityScore(jobCity, desiredCity);

        // 综合评分（6维度加权）
        int totalScore = (int) Math.round(
                skillScore * SKILL_WEIGHT +
                experienceScore * EXPERIENCE_WEIGHT +
                educationScore * EDUCATION_WEIGHT +
                jobTitleScore * JOB_TITLE_WEIGHT +
                salaryScore * SALARY_WEIGHT +
                cityScore * CITY_WEIGHT);

        // 限制范围
        totalScore = Math.min(95, Math.max(10, totalScore));

        MatchResult result = new MatchResult();
        result.matchScore = totalScore;
        result.matchedSkills = matched;
        result.missingSkills = missing;
        result.matchedAbilities = new ArrayList<>();
        result.missingAbilities = new ArrayList<>();
        result.experienceMatch = experienceMatch;
        result.educationMatch = educationMatch;
        result.userYears = userYears;
        result.jobExperience = jobExperience;
        result.userEducation = userEducation;
        result.jobEducation = jobEducation;

        return result;
    }

    /**
     * 计算薪资匹配度
     */
    private double calculateSalaryScore(int salaryMin, int salaryMax, int expectedMin, int expectedMax) {
        // 如果没有期望薪资或岗位薪资，返回默认分
        if (expectedMin == 0 && expectedMax == 0) return 80.0;
        if (salaryMin == 0 && salaryMax == 0) return 80.0;

        // 计算薪资中位数
        int jobSalaryMid = (salaryMin + salaryMax) / 2;
        int expectedSalaryMid = (expectedMin + expectedMax) / 2;

        if (expectedSalaryMid == 0) return 80.0;

        // 计算薪资匹配度
        double ratio = (double) jobSalaryMid / expectedSalaryMid;

        if (ratio >= 0.8 && ratio <= 1.2) {
            // 薪资匹配：80%-120%
            return 100.0;
        } else if (ratio > 1.2) {
            // 薪资高于期望：每高10%扣5分
            double overRatio = (ratio - 1.2) * 100;
            return Math.max(60, 100 - overRatio * 0.5);
        } else {
            // 薪资低于期望：每低10%扣10分
            double underRatio = (1.0 - ratio) * 100;
            return Math.max(40, 100 - underRatio * 1.0);
        }
    }

    /**
     * 计算城市匹配度
     */
    private double calculateCityScore(String jobCity, String desiredCity) {
        // 如果没有期望城市或岗位城市，返回默认分
        if (desiredCity == null || desiredCity.isEmpty()) return 80.0;
        if (jobCity == null || jobCity.isEmpty()) return 80.0;

        // 完全匹配
        if (jobCity.equals(desiredCity)) return 100.0;

        // 同一城市等级
        int jobCityLevel = CITY_LEVEL.getOrDefault(jobCity, 4);
        int desiredCityLevel = CITY_LEVEL.getOrDefault(desiredCity, 4);

        if (jobCityLevel == desiredCityLevel) {
            return 85.0; // 同一等级城市
        } else if (Math.abs(jobCityLevel - desiredCityLevel) == 1) {
            return 70.0; // 相邻等级城市
        } else {
            return 50.0; // 不同等级城市
        }
    }

    /**
     * 从文本中提取能力关键词（语义匹配）
     */
    private Set<String> extractAbilities(String text) {
        Set<String> abilities = new HashSet<>();
        if (text == null || text.isEmpty()) return abilities;

        String lower = text.toLowerCase();

        // 遍历能力关键词映射
        for (Map.Entry<String, List<String>> entry : ABILITY_KEYWORDS.entrySet()) {
            String abilityName = entry.getKey();
            List<String> keywords = entry.getValue();

            for (String keyword : keywords) {
                if (lower.contains(keyword.toLowerCase())) {
                    abilities.add(abilityName);
                    break;  // 找到一个关键词就代表有这个能力
                }
            }
        }

        // 提取软技能
        for (String softSkill : SOFT_SKILL_KEYWORDS) {
            if (lower.contains(softSkill)) {
                abilities.add(softSkill);
            }
        }

        return abilities;
    }

    /**
     * 从简历中提取项目描述
     */
    private String extractProjectDescription(Map<String, Object> resumeData) {
        StringBuilder sb = new StringBuilder();

        // 从 card_structure 提取项目经历
        Object cardStructure = resumeData.get("cardStructure");
        if (cardStructure instanceof Map) {
            Map<?, ?> card = (Map<?, ?>) cardStructure;

            // 项目经历
            Object projects = card.get("projects");
            if (projects instanceof List) {
                for (Object project : (List<?>) projects) {
                    if (project instanceof Map) {
                        Map<?, ?> p = (Map<?, ?>) project;
                        Object desc = p.get("description");
                        if (desc != null) sb.append(desc).append(" ");
                        Object responsibility = p.get("responsibility");
                        if (responsibility != null) sb.append(responsibility).append(" ");
                    }
                }
            }

            // 工作经历
            Object workExperience = card.get("workExperience");
            if (workExperience instanceof List) {
                for (Object work : (List<?>) workExperience) {
                    if (work instanceof Map) {
                        Map<?, ?> w = (Map<?, ?>) work;
                        Object desc = w.get("description");
                        if (desc != null) sb.append(desc).append(" ");
                    }
                }
            }
        }

        return sb.toString();
    }

    /**
     * 岗位匹配（期望岗位 vs 岗位标题）
     */
    private double calculateJobTitleScore(String jobTitle, String desiredJob) {
        if (desiredJob == null || desiredJob.isEmpty()) return 80.0;  // 无期望岗位
        if (jobTitle == null || jobTitle.isEmpty()) return 80.0;      // 无岗位标题

        // 转小写
        String jobLower = jobTitle.toLowerCase();
        String desiredLower = desiredJob.toLowerCase();

        // 1. 精确匹配
        if (jobLower.equals(desiredLower)) return 100.0;

        // 2. 包含匹配
        if (jobLower.contains(desiredLower) || desiredLower.contains(jobLower)) return 100.0;

        // 3. 方向匹配检查（核心改进）
        String jobDirection = getJobDirection(jobLower);
        String desiredDirection = getJobDirection(desiredLower);

        if (jobDirection != null && desiredDirection != null) {
            if (!jobDirection.equals(desiredDirection)) {
                // 方向完全不同，直接给低分
                return 5.0;
            }
        }

        // 4. 关键词匹配（提取核心词）
        Set<String> jobKeywords = extractJobKeywords(jobTitle);
        Set<String> desiredKeywords = extractJobKeywords(desiredJob);

        if (jobKeywords.isEmpty() || desiredKeywords.isEmpty()) return 40.0;

        // 计算关键词交集
        Set<String> intersection = new HashSet<>(jobKeywords);
        intersection.retainAll(desiredKeywords);

        if (!intersection.isEmpty()) {
            // 有交集，按比例计算
            double ratio = (double) intersection.size() / Math.min(jobKeywords.size(), desiredKeywords.size());
            return 60 + ratio * 40;  // 60-100分
        }

        // 5. 无匹配
        return 10.0;
    }

    /**
     * 关键词 → 数据库 job_type 枚举值映射（对齐前端枚举定义）
     * <p>
     * 前端固定枚举：FRONTEND, JAVA_BACKEND, GO_BACKEND, PRODUCT, UI_DESIGN, DATA, OPERATION, OTHER
     * 通用方向词（如"后端"、"python"）不在此映射中，走 JOB_DIRECTION 回退 → keyword LIKE 兜底。
     * </p>
     */
    private static final Map<String, String> KEYWORD_TO_ENUM = new HashMap<>();
    static {
        // 前端 FRONTEND
        for (String kw : Arrays.asList("前端", "frontend", "react", "vue", "angular",
                "javascript", "typescript", "web前端", "h5", "小程序")) {
            KEYWORD_TO_ENUM.put(kw, "FRONTEND");
        }
        // Java 后端 JAVA_BACKEND
        for (String kw : Arrays.asList("java", "java后端")) {
            KEYWORD_TO_ENUM.put(kw, "JAVA_BACKEND");
        }
        // Go 后端 GO_BACKEND
        for (String kw : Arrays.asList("go", "golang")) {
            KEYWORD_TO_ENUM.put(kw, "GO_BACKEND");
        }
        // 产品 PRODUCT
        for (String kw : Arrays.asList("产品经理", "产品", "pm")) {
            KEYWORD_TO_ENUM.put(kw, "PRODUCT");
        }
        // UI设计 UI_DESIGN
        for (String kw : Arrays.asList("ui", "ue", "交互设计", "ui设计", "视觉设计")) {
            KEYWORD_TO_ENUM.put(kw, "UI_DESIGN");
        }
        // 数据 DATA
        for (String kw : Arrays.asList("数据分析", "数据开发", "大数据", "数据仓库", "etl", "bi")) {
            KEYWORD_TO_ENUM.put(kw, "DATA");
        }
        // 运营 OPERATION
        for (String kw : Arrays.asList("运营", "新媒体运营", "内容运营", "用户运营")) {
            KEYWORD_TO_ENUM.put(kw, "OPERATION");
        }
        // 其他 OTHER（测试、算法、运维、安全、Android、C++ 等）
        for (String kw : Arrays.asList("测试", "算法", "运维", "安全", "android", "安卓",
                "c++", "devops", "机器学习", "深度学习")) {
            KEYWORD_TO_ENUM.put(kw, "OTHER");
        }
    }

    /**
     * 搜索关键词扩展映射（主关键词 → 同义/相关关键词列表，用于 title LIKE 模糊搜索）
     * <p>
     * 解决岗位标题用词不一致的问题：用户搜"前端"，但标题可能写 "Frontend Developer"、"React工程师" 等。
     * </p>
     */
    private static final Map<String, List<String>> SEARCH_KEYWORD_EXPANSION = new HashMap<>();
    static {
        SEARCH_KEYWORD_EXPANSION.put("前端", Arrays.asList("前端", "frontend", "react", "vue", "angular", "javascript", "typescript", "web"));
        SEARCH_KEYWORD_EXPANSION.put("后端", Arrays.asList("后端", "backend", "服务端", "server", "java", "python", "go", "spring"));
        SEARCH_KEYWORD_EXPANSION.put("java", Arrays.asList("java", "spring", "后端", "backend"));
        SEARCH_KEYWORD_EXPANSION.put("go", Arrays.asList("go", "golang", "后端", "backend"));
        SEARCH_KEYWORD_EXPANSION.put("python", Arrays.asList("python", "django", "flask", "fastapi", "数据分析"));
        SEARCH_KEYWORD_EXPANSION.put("测试", Arrays.asList("测试", "test", "qa", "质量"));
        SEARCH_KEYWORD_EXPANSION.put("算法", Arrays.asList("算法", "机器学习", "深度学习", "ai", "人工智能", "nlp", "cv"));
        SEARCH_KEYWORD_EXPANSION.put("运维", Arrays.asList("运维", "devops", "sre", "k8s", "docker", "linux"));
        SEARCH_KEYWORD_EXPANSION.put("安全", Arrays.asList("安全", "security", "渗透测试", "安全攻防", "安全工程师"));
        SEARCH_KEYWORD_EXPANSION.put("android", Arrays.asList("android", "安卓", "移动端", "手机开发", "app开发", "flutter"));
        SEARCH_KEYWORD_EXPANSION.put("c++", Arrays.asList("c++", "cpp", "后端", "backend"));
        SEARCH_KEYWORD_EXPANSION.put("数据", Arrays.asList("数据", "data", "大数据", "hadoop", "spark", "flink", "etl"));
        SEARCH_KEYWORD_EXPANSION.put("产品", Arrays.asList("产品", "product", "pm", "产品经理"));
        SEARCH_KEYWORD_EXPANSION.put("设计", Arrays.asList("设计", "ui", "ue", "交互", "视觉"));
        SEARCH_KEYWORD_EXPANSION.put("运营", Arrays.asList("运营", "operation", "新媒体", "内容运营", "用户运营"));
    }

    /**
     * 从用户消息中提取搜索关键词列表（用于 title LIKE 模糊搜索）
     * <p>
     * 返回同义/相关关键词列表，SQL 用 OR 拼接。如"前端" → ["前端","frontend","react","vue","angular","javascript","typescript","web"]。
     * 未命中扩展映射时返回主关键词单元素列表。
     * </p>
     *
     * @param message 用户消息
     * @return 关键词列表
     */
    public List<String> extractSearchKeywords(String message) {
        if (message == null || message.isEmpty()) return Collections.emptyList();
        String lower = message.toLowerCase();
        for (Map.Entry<String, List<String>> entry : SEARCH_KEYWORD_EXPANSION.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return Collections.emptyList();
    }

    /**
     * 从用户消息中提取岗位类型枚举值（供搜索过滤用）
     * <p>
     * 匹配策略：
     * ① 先匹配 KEYWORD_TO_ENUM 中的具体关键词（如"java"→JAVA_BACKEND，"前端"→FRONTEND）；
     * ② 未命中时，匹配 JOB_DIRECTION 方向关键词（如"后端"、"开发"），
     *    但方向关键词在数据库中没有精确对应的枚举值，返回空字符串让搜索仅依赖 keyword LIKE 过滤。
     * </p>
     *
     * @param message 用户消息，如"推荐前端岗位"
     * @return 数据库 job_type 枚举值（如"FRONTEND"），未识别返回空字符串
     */
    public String extractJobTypeFromMessage(String message) {
        if (message == null || message.isEmpty()) return "";
        String lower = message.toLowerCase();

        // ① 优先匹配具体关键词（精确映射数据库枚举值）
        for (Map.Entry<String, String> entry : KEYWORD_TO_ENUM.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // ② 方向关键词匹配（"后端"等通用方向无精确枚举，返回空让 keyword LIKE 兜底）
        for (Map.Entry<String, List<String>> entry : JOB_DIRECTION.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    // 方向名本身是枚举值（如"前端开发"→"FRONTEND"）则返回，否则空
                    String enumVal = KEYWORD_TO_ENUM.get(entry.getKey());
                    return enumVal != null ? enumVal : "";
                }
            }
        }
        return "";
    }

    /**
     * 判断岗位属于哪个方向
     */
    private String getJobDirection(String jobTitle) {
        for (Map.Entry<String, List<String>> entry : JOB_DIRECTION.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (jobTitle.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /**
     * 提取岗位关键词
     */
    private Set<String> extractJobKeywords(String jobTitle) {
        Set<String> keywords = new HashSet<>();
        String lower = jobTitle.toLowerCase();

        // 常见岗位关键词
        String[] commonKeywords = {
            "java", "python", "go", "c++", "前端", "后端", "全栈",
            "开发", "工程师", "架构", "测试", "运维", "产品", "设计",
            "数据", "算法", "安全", "移动", "android", "ios",
            "初级", "中级", "高级", "资深", "专家", "总监"
        };

        for (String keyword : commonKeywords) {
            if (lower.contains(keyword)) {
                keywords.add(keyword);
            }
        }

        return keywords;
    }

    /**
     * 技能匹配（支持同义词）
     */
    private boolean skillMatchesWithSynonyms(String requiredSkill, Set<String> userSkills) {
        String lower = requiredSkill.toLowerCase();

        // 1. 精确匹配
        if (userSkills.contains(lower)) return true;

        // 2. 包含匹配
        for (String userSkill : userSkills) {
            if (userSkill.contains(lower) || lower.contains(userSkill)) return true;
        }

        // 3. 同义词匹配
        for (Map.Entry<String, List<String>> entry : SKILL_SYNONYMS.entrySet()) {
            List<String> synonyms = entry.getValue();
            boolean requiredInGroup = synonyms.stream().anyMatch(s -> s.equalsIgnoreCase(lower));
            if (requiredInGroup) {
                // 岗位要求在这个同义词组中，检查用户技能是否也在
                for (String synonym : synonyms) {
                    if (userSkills.contains(synonym.toLowerCase())) return true;
                }
            }
        }

        return false;
    }


    // ==================== 数据提取 ====================

    /**
     * 从岗位数据中提取技能列表
     */
    private List<String> extractJobSkills(Map<String, Object> jobData) {
        List<String> skills = new ArrayList<>();

        // 尝试从 profile.coreSkills 提取
        Object profile = jobData.get("profile");
        if (profile instanceof Map) {
            Object coreSkills = ((Map<?, ?>) profile).get("coreSkills");
            if (coreSkills instanceof List) {
                for (Object skill : (List<?>) coreSkills) {
                    if (skill instanceof Map) {
                        String name = (String) ((Map<?, ?>) skill).get("name");
                        if (name != null && !name.isEmpty()) {
                            skills.add(name);
                        }
                    } else if (skill instanceof String) {
                        skills.add((String) skill);
                    }
                }
            }
        }

        // 尝试从 skillTags 提取
        if (skills.isEmpty()) {
            Object skillTags = jobData.get("skillTags");
            if (skillTags instanceof List) {
                for (Object tag : (List<?>) skillTags) {
                    if (tag instanceof String) {
                        skills.add((String) tag);
                    }
                }
            }
        }

        // 尝试从 jdText 提取关键词（简单匹配）
        if (skills.isEmpty()) {
            String jdText = (String) jobData.get("jdText");
            if (jdText != null) {
                String[] commonSkills = {"Java", "Python", "JavaScript", "TypeScript", "Go", "C++",
                        "Spring", "Spring Boot", "MyBatis", "MySQL", "Redis", "MongoDB",
                        "Docker", "Kubernetes", "K8s", "Linux", "Nginx",
                        "React", "Vue", "Angular", "HTML", "CSS",
                        "Kafka", "RabbitMQ", "Elasticsearch", "微服务"};
                for (String skill : commonSkills) {
                    if (jdText.contains(skill)) {
                        skills.add(skill);
                    }
                }
            }
        }

        return skills;
    }

    /**
     * 从用户简历中提取技能列表
     */
    @SuppressWarnings("unchecked")
    private List<String> extractUserSkills(Map<String, Object> resumeData) {
        List<String> skills = new ArrayList<>();

        // 1. 先尝试从顶层 skills 字段提取
        Object skillsObj = resumeData.get("skills");
        if (skillsObj instanceof List) {
            for (Object skill : (List<?>) skillsObj) {
                if (skill instanceof String) {
                    skills.add((String) skill);
                }
            }
        }

        // 2. 从 ability_model.sub_dimensions.skills 提取
        if (skills.isEmpty()) {
            Object abilityModel = resumeData.get("abilityModel");
            if (abilityModel instanceof Map) {
                Object subDimensions = ((Map<?, ?>) abilityModel).get("subDimensions");
                if (subDimensions instanceof Map) {
                    Object subSkills = ((Map<?, ?>) subDimensions).get("skills");
                    if (subSkills instanceof List) {
                        for (Object skill : (List<?>) subSkills) {
                            if (skill instanceof String) {
                                skills.add((String) skill);
                            }
                        }
                    }
                }
            }
        }

        // 3. 如果还是空，尝试从 cardStructure.sections 中提取技能章节
        if (skills.isEmpty()) {
            Object cardStructure = resumeData.get("cardStructure");
            if (cardStructure instanceof Map) {
                Map<?, ?> card = (Map<?, ?>) cardStructure;
                Object sections = card.get("sections");
                if (sections instanceof List) {
                    for (Object section : (List<?>) sections) {
                        if (section instanceof Map) {
                            String title = (String) ((Map<?, ?>) section).get("title");
                            if (title != null && (title.contains("技能") || title.contains("专业")
                                    || title.toLowerCase().contains("skill"))) {
                                // 从 points 中提取技能
                                Object points = ((Map<?, ?>) section).get("points");
                                if (points instanceof List) {
                                    for (Object point : (List<?>) points) {
                                        if (point instanceof Map) {
                                            String text = (String) ((Map<?, ?>) point).get("text");
                                            if (text != null) {
                                                // 按逗号、顿号等分割技能
                                                for (String skill : text.split("[，,、；;\\s]+")) {
                                                    String trimmed = skill.trim();
                                                    if (trimmed.length() >= 2 && trimmed.length() <= 20) {
                                                        skills.add(trimmed);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        log.info("提取到用户技能: {}", skills);
        return skills;
    }

    /**
     * 从用户简历中提取学历
     */
    @SuppressWarnings("unchecked")
    private String extractUserEducation(Map<String, Object> resumeData) {
        // 1. 先尝试从顶层字段提取
        String education = (String) resumeData.get("education");
        if (education != null && !education.isEmpty()) {
            return education;
        }

        // 2. 从 cardStructure.sections 中提取教育背景
        Object cardStructure = resumeData.get("cardStructure");
        if (cardStructure instanceof Map) {
            Map<?, ?> card = (Map<?, ?>) cardStructure;
            Object sections = card.get("sections");
            if (sections instanceof List) {
                for (Object section : (List<?>) sections) {
                    if (section instanceof Map) {
                        String title = (String) ((Map<?, ?>) section).get("title");
                        if (title != null && (title.contains("教育") || title.contains("学历")
                                || title.toLowerCase().contains("education"))) {
                            // 从 points 中提取学历信息
                            Object points = ((Map<?, ?>) section).get("points");
                            if (points instanceof List) {
                                for (Object point : (List<?>) points) {
                                    if (point instanceof Map) {
                                        String text = (String) ((Map<?, ?>) point).get("text");
                                        if (text != null) {
                                            // 检查是否包含学历关键词
                                            if (text.contains("本科") || text.contains("学士")) return "本科";
                                            if (text.contains("硕士") || text.contains("研究生")) return "硕士";
                                            if (text.contains("博士")) return "博士";
                                            if (text.contains("大专") || text.contains("专科")) return "大专";
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return "";
    }

    /**
     * 从用户简历中提取工作年限
     */
    @SuppressWarnings("unchecked")
    private Integer extractUserWorkYears(Map<String, Object> resumeData) {
        // 1. 先尝试从顶层字段提取
        String workYears = (String) resumeData.get("workYears");
        if (workYears != null && !workYears.isEmpty()) {
            return parseWorkYears(workYears);
        }

        // 2. 从 ability_model.sub_dimensions.work_years 提取
        Object abilityModel = resumeData.get("abilityModel");
        if (abilityModel instanceof Map) {
            Object subDimensions = ((Map<?, ?>) abilityModel).get("subDimensions");
            if (subDimensions instanceof Map) {
                Object workYearsObj = ((Map<?, ?>) subDimensions).get("workYears");
                if (workYearsObj instanceof String) {
                    return parseWorkYears((String) workYearsObj);
                }
                if (workYearsObj instanceof Number) {
                    return ((Number) workYearsObj).intValue();
                }
            }
        }

        // 3. 从 cardStructure.sections 中查找工作年限信息
        Object cardStructure = resumeData.get("cardStructure");
        if (cardStructure instanceof Map) {
            Map<?, ?> card = (Map<?, ?>) cardStructure;
            Object sections = card.get("sections");
            if (sections instanceof List) {
                for (Object section : (List<?>) sections) {
                    if (section instanceof Map) {
                        String title = (String) ((Map<?, ?>) section).get("title");
                        if (title != null && (title.contains("工作") || title.contains("实习")
                                || title.toLowerCase().contains("work")
                                || title.toLowerCase().contains("experience"))) {
                            // 从 points 中提取工作年限
                            Object points = ((Map<?, ?>) section).get("points");
                            if (points instanceof List && !((List<?>) points).isEmpty()) {
                                // 尝试从第一个 point 中提取年份信息
                                Object firstPoint = ((List<?>) points).get(0);
                                if (firstPoint instanceof Map) {
                                    String text = (String) ((Map<?, ?>) firstPoint).get("text");
                                    if (text != null) {
                                        // 使用正则提取年份
                                        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)\\s*年").matcher(text);
                                        if (matcher.find()) {
                                            return Integer.parseInt(matcher.group(1));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return 0;
    }

    /**
     * 从用户简历中提取期望岗位
     */
    @SuppressWarnings("unchecked")
    private String extractUserDesiredJob(Map<String, Object> resumeData) {
        // 1. 先尝试从顶层字段提取
        String desiredJob = (String) resumeData.get("desiredJob");
        if (desiredJob != null && !desiredJob.isEmpty()) {
            return desiredJob;
        }

        // 2. 从 cardStructure 中提取
        Object cardStructure = resumeData.get("cardStructure");
        if (cardStructure instanceof Map) {
            Map<?, ?> card = (Map<?, ?>) cardStructure;

            // 尝试从 desiredJob 字段提取
            Object desiredJobObj = card.get("desiredJob");
            if (desiredJobObj instanceof String) {
                return (String) desiredJobObj;
            }

            // 尝试从 jobIntention 提取
            Object jobIntention = card.get("jobIntention");
            if (jobIntention instanceof Map) {
                Object position = ((Map<?, ?>) jobIntention).get("position");
                if (position instanceof String) {
                    return (String) position;
                }
            }

            // 从 sections 中提取求职意向
            Object sections = card.get("sections");
            if (sections instanceof List) {
                for (Object section : (List<?>) sections) {
                    if (section instanceof Map) {
                        String title = (String) ((Map<?, ?>) section).get("title");
                        if (title != null && (title.contains("求职意向") || title.contains("求职期望")
                                || title.toLowerCase().contains("objective"))) {
                            Object points = ((Map<?, ?>) section).get("points");
                            if (points instanceof List) {
                                for (Object point : (List<?>) points) {
                                    if (point instanceof Map) {
                                        String text = (String) ((Map<?, ?>) point).get("text");
                                        if (text != null) {
                                            // 尝试提取岗位名称
                                            if (text.contains("岗位") || text.contains("职位") || text.contains("方向")) {
                                                String[] parts = text.split("[：:、,，]");
                                                for (String part : parts) {
                                                    if (part.contains("工程师") || part.contains("开发") || part.contains("设计")
                                                            || part.contains("产品") || part.contains("测试") || part.contains("运维")) {
                                                        return part.trim();
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return "";
    }

    /**
     * 从用户简历中提取期望城市
     */
    @SuppressWarnings("unchecked")
    private String extractUserDesiredCity(Map<String, Object> resumeData) {
        // 1. 从 cardStructure 中提取
        Object cardStructure = resumeData.get("cardStructure");
        if (cardStructure instanceof Map) {
            Map<?, ?> card = (Map<?, ?>) cardStructure;

            // 从 sections 中提取求职意向
            Object sections = card.get("sections");
            if (sections instanceof List) {
                for (Object section : (List<?>) sections) {
                    if (section instanceof Map) {
                        String title = (String) ((Map<?, ?>) section).get("title");
                        if (title != null && (title.contains("求职意向") || title.contains("求职期望")
                                || title.toLowerCase().contains("objective"))) {
                            Object points = ((Map<?, ?>) section).get("points");
                            if (points instanceof List) {
                                for (Object point : (List<?>) points) {
                                    if (point instanceof Map) {
                                        String text = (String) ((Map<?, ?>) point).get("text");
                                        if (text != null) {
                                            // 尝试提取城市
                                            for (String city : CITY_LEVEL.keySet()) {
                                                if (text.contains(city)) {
                                                    return city;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return "";
    }

    /**
     * 从用户简历中提取期望最低薪资
     */
    @SuppressWarnings("unchecked")
    private Integer extractUserExpectedSalaryMin(Map<String, Object> resumeData) {
        // 从 cardStructure 中提取
        Object cardStructure = resumeData.get("cardStructure");
        if (cardStructure instanceof Map) {
            Map<?, ?> card = (Map<?, ?>) cardStructure;

            // 从 sections 中提取求职意向
            Object sections = card.get("sections");
            if (sections instanceof List) {
                for (Object section : (List<?>) sections) {
                    if (section instanceof Map) {
                        String title = (String) ((Map<?, ?>) section).get("title");
                        if (title != null && (title.contains("求职意向") || title.contains("求职期望")
                                || title.toLowerCase().contains("objective"))) {
                            Object points = ((Map<?, ?>) section).get("points");
                            if (points instanceof List) {
                                for (Object point : (List<?>) points) {
                                    if (point instanceof Map) {
                                        String text = (String) ((Map<?, ?>) point).get("text");
                                        if (text != null) {
                                            // 尝试提取薪资范围，如 "25K-35K" 或 "25k-35k"
                                            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)[Kk]\\s*[-~]\\s*(\\d+)[Kk]").matcher(text);
                                            if (matcher.find()) {
                                                return Integer.parseInt(matcher.group(1));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return 0;
    }

    /**
     * 从用户简历中提取期望最高薪资
     */
    @SuppressWarnings("unchecked")
    private Integer extractUserExpectedSalaryMax(Map<String, Object> resumeData) {
        // 从 cardStructure 中提取
        Object cardStructure = resumeData.get("cardStructure");
        if (cardStructure instanceof Map) {
            Map<?, ?> card = (Map<?, ?>) cardStructure;

            // 从 sections 中提取求职意向
            Object sections = card.get("sections");
            if (sections instanceof List) {
                for (Object section : (List<?>) sections) {
                    if (section instanceof Map) {
                        String title = (String) ((Map<?, ?>) section).get("title");
                        if (title != null && (title.contains("求职意向") || title.contains("求职期望")
                                || title.toLowerCase().contains("objective"))) {
                            Object points = ((Map<?, ?>) section).get("points");
                            if (points instanceof List) {
                                for (Object point : (List<?>) points) {
                                    if (point instanceof Map) {
                                        String text = (String) ((Map<?, ?>) point).get("text");
                                        if (text != null) {
                                            // 尝试提取薪资范围，如 "25K-35K" 或 "25k-35k"
                                            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)[Kk]\\s*[-~]\\s*(\\d+)[Kk]").matcher(text);
                                            if (matcher.find()) {
                                                return Integer.parseInt(matcher.group(2));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return 0;
    }

    /**
     * 解析工作年限字符串
     */
    private Integer parseWorkYears(String workYears) {
        if (workYears == null || workYears.isEmpty()) return 0;
        // 处理 "3年"、"3-5年"、"FRESH" 等格式
        if ("FRESH".equalsIgnoreCase(workYears) || "应届".equals(workYears)) return 0;
        try {
            // 提取第一个数字
            String num = workYears.replaceAll("[^0-9]", "");
            if (num.isEmpty()) return 0;
            return Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String errorJson(String message) {
        try {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", message);
            error.put("matchScore", 0);
            return objectMapper.writeValueAsString(error);
        } catch (Exception e) {
            return "{\"error\":\"" + message + "\",\"matchScore\":0}";
        }
    }

    // ==================== 结果类 ====================

    private static class MatchResult {
        int matchScore;
        List<String> matchedSkills;
        List<String> missingSkills;
        List<String> matchedAbilities;   // 匹配的能力
        List<String> missingAbilities;   // 缺失的能力
        boolean experienceMatch;
        boolean educationMatch;
        int userYears;
        int jobExperience;
        String userEducation;
        String jobEducation;

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("matchScore", matchScore);
            map.put("matchedSkills", matchedSkills);
            map.put("missingSkills", missingSkills);
            map.put("matchedAbilities", matchedAbilities);
            map.put("missingAbilities", missingAbilities);
            map.put("experienceMatch", experienceMatch);
            map.put("educationMatch", educationMatch);
            map.put("userYears", userYears);
            map.put("jobExperience", jobExperience);
            map.put("userEducation", userEducation);
            map.put("jobEducation", jobEducation);
            return map;
        }
    }
}
