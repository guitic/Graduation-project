package com.graduation.mentor.config;

import com.graduation.mentor.common.CommentStatus;
import com.graduation.mentor.entity.Clazz;
import com.graduation.mentor.entity.Comment;
import com.graduation.mentor.entity.CommentAudit;
import com.graduation.mentor.entity.Major;
import com.graduation.mentor.entity.Student;
import com.graduation.mentor.entity.Teacher;
import com.graduation.mentor.entity.TeacherStudent;
import com.graduation.mentor.entity.User;
import com.graduation.mentor.mapper.ClazzMapper;
import com.graduation.mentor.mapper.CommentAuditMapper;
import com.graduation.mentor.mapper.CommentMapper;
import com.graduation.mentor.mapper.MajorMapper;
import com.graduation.mentor.mapper.StudentMapper;
import com.graduation.mentor.mapper.TeacherMapper;
import com.graduation.mentor.mapper.TeacherStudentMapper;
import com.graduation.mentor.mapper.UserMapper;
import com.graduation.mentor.service.AnalysisService;
import com.graduation.mentor.service.AuditService;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 演示数据初始化器。
 *
 * <p><b>为什么用 Java 而不是 data.sql</b>：
 *
 * <ol>
 *   <li><b>时间必须是相对的</b>。硬编码日期的种子在三个月后会全部落到趋势窗口之外， 演示时趋势图空白——这是真实发生过的事故。这里全部用 {@code
 *       YearMonth.now().minusMonths(n)}。
 *   <li><b>密码必须运行时哈希</b>。SQL 写死 BCrypt 串无法验证"哈希是否由配置的编码器产生"。
 *   <li><b>跨库一致</b>。不依赖 {@code DATE_SUB} 等方言，H2 demo profile 同样可跑。
 * </ol>
 *
 * <p><b>样本量设计（钉死三档隐私门槛 + 桶级门禁）</b>：
 *
 * <table border="1">
 *   <tr><th>班级</th><th>已发布</th><th>月份分布</th><th>钉住什么</th></tr>
 *   <tr><td>计科2204</td><td>2</td><td>同一月</td><td>词云空（n&lt;3 的下一点）</td></tr>
 *   <tr><td>计科2203</td><td>3</td><td>同一月</td><td>词云放行（阈值点）+ 饼图空</td></tr>
 *   <tr><td>计科2207</td><td>4</td><td>同一月</td><td>饼图空（n&lt;5 的紧邻下一点）</td></tr>
 *   <tr><td>计科2205</td><td>5</td><td>同一月</td><td>饼图放行（阈值点）</td></tr>
 *   <tr><td>计科2201</td><td>40</td><td>19 / 19 / 2</td><td>图级放行，但第三月桶级 n=2 → 下钻禁用</td></tr>
 * </table>
 *
 * 若只有 n=3 与 n=5 两个班，实现写成 {@code n<4} 会同时通过（3&lt;4 空 ✓、5&lt;4 画 ✓）， 错误完全测不出来。加入 n=4 后 {@code n<4}
 * 必翻红。 <b>只测阈值两侧、不测阈值本身，任何中间错值都会全绿通过。</b>
 *
 * <p>边缘班（5/4/3/2 条）必须落在**同一自然月**，否则桶级样本被摊薄， "图正常画但某点不可下钻"这个演示点会消失。
 *
 * <p><b>开关</b>：由 {@code app.demo-seed.enabled} 控制，默认 <b>关闭</b>，只在 {@code demo} profile 打开。 生产库里凭空出现
 * 70 条演示点评是数据事故，不能靠"记得别在生产启动"来规避。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.demo-seed", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    private static final String DEMO_PASSWORD = "123456";

    /** 相对当前月的偏移：-3 / -2 / -1 月，避开"未完成当月" */
    private static final int M3 = 3;

    private static final int M2 = 2;
    private static final int M1 = 1;

    private final UserMapper userMapper;
    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;
    private final ClazzMapper clazzMapper;
    private final MajorMapper majorMapper;
    private final TeacherStudentMapper teacherStudentMapper;
    private final CommentMapper commentMapper;
    private final CommentAuditMapper auditMapper;
    private final AnalysisService analysisService;
    private final PasswordEncoder passwordEncoder;

    /** 正向倾向 */
    private static final String[] POS = {
        "本学期学习态度端正，课堂参与积极，作业完成质量较高。期末复习阶段能主动整理知识框架，进步明显，希望继续保持这种踏实的学习节奏。",
        "该生专业基础扎实，动手能力强，在课程设计中独立完成了核心模块，思路清晰、代码规范，团队协作中也乐于帮助同学，表现优异。",
        "学习目标明确，计划性强，能够合理安排时间，遇到问题主动请教并认真复盘。近期成绩稳步提升，发展潜力较好。",
        "课堂发言积极，思考深入，对专业问题有自己独立的见解。实践环节认真细致，实验报告规范完整，值得肯定。",
        "态度认真踏实，自律性强，出勤情况良好。在小组项目中主动承担协调工作，责任心强，同学评价很高。",
        "进步显著，从期中到期末成绩提升明显。能虚心接受建议并及时改进，学习方法逐渐成熟，希望继续保持。",
        "学习热情高，对专业方向兴趣浓厚，主动参与学科竞赛并取得不错成绩。基础扎实，可塑性很强。",
        "平时作业按时完成，书写工整，思考过程完整。遇到难点愿意钻研，不轻易放弃，这种钻研精神很难得。",
        "综合表现优秀，学习主动性强，能带动周围同学一起讨论问题。建议继续保持并适当拓宽知识面。",
        "该生踏实肯干，执行力强，布置的任务总能高质量完成。沟通表达清晰，与导师配合顺畅。",
        "本学期适应良好，状态稳定，课堂专注度高。对专业课程理解到位，实践动手能力提升很快，前景可期。",
        "学习态度积极，愿意尝试新的方法，敢于表达不同观点。团队意识好，协作能力突出，整体表现令人满意。",
        "基础较为扎实，复习有计划，错题整理认真。近期测验成绩稳定在班级前列，进步值得表扬。",
        "该生思维活跃，善于总结规律，学习效率高。在毕业设计前期调研中表现出很强的自主学习能力。",
        "态度谦虚，乐于请教，进步空间把握得好。实验环节认真细致，数据记录规范，体现了良好的科研素养。",
        "课堂互动积极，回答问题条理清楚。作业质量稳定，独立思考能力强，是很有潜力的学生。",
        "该生责任心强，担任小组组长期间组织有序，任务分配合理，团队氛围融洽，综合能力突出。",
        "学习自觉性高，能主动拓展课外阅读，对专业前沿保持关注。表达能力和逻辑思维都有明显提升。",
        "本学期整体状态良好，情绪稳定，与同学相处融洽。学业上稳步推进，各项任务按时完成，值得肯定。",
        "专业兴趣浓厚，实践项目完成度高，能独立排查并解决问题。建议在理论深度上再下功夫，前景很好。"
    };

    /** 中性 / 混合表达 */
    private static final String[] NEU = {
        "本学期整体按计划推进，课程任务基本完成。课堂上偶有走神，但作业都能按时交，后续需要加强课堂专注度。",
        "该生学习状态平稳，成绩处于班级中游。基础知识掌握尚可，遇到综合性题目时思路还不够开阔，建议多做专项训练。",
        "平时表现中规中矩，出勤正常，作业完成情况一般。对专业课程的兴趣还需要进一步培养。",
        "期中成绩与上学期基本持平，没有明显起伏。学习方法较为固定，可以尝试调整节奏，看能否突破瓶颈。",
        "课堂参与度一般，主动发言较少，但被提问时能答上来。建议多参与讨论，锻炼表达能力。",
        "该生性格内向，与同学交流不多，学习上比较独立。任务完成情况尚可，希望在团队协作上多投入一些。",
        "本学期任务完成度符合要求，实验报告格式规范但分析部分偏简单。整体表现稳定，暂无突出亮点。",
        "学习态度尚可，偶尔有拖延现象，但能在截止前完成。建议提前规划，避免集中赶工影响质量。",
        "该生对理论课兴趣一般，实践环节相对积极。成绩分布不均衡，需要注意补齐短板。",
        "近期状态有些波动，可能与课程难度增加有关。已与其沟通，建议调整复习节奏并加强基础巩固。",
        "整体表现符合预期，各项指标在正常范围内。希望下学期在主动性上有所提升。",
        "该生基础尚可，但答题时粗心的情况仍有出现。已提醒其检查习惯，后续观察改进情况。"
    };

    /** 负向倾向 */
    private static final String[] NEG = {
        "本学期出勤情况较差，多次迟到，作业存在敷衍应付的情况，质量不高。已当面提醒，但改进不明显，需要重点关注。",
        "学习主动性不足，课堂参与度低，遇到困难容易退缩。基础薄弱且缺乏复习计划，成绩下滑明显，建议尽快制定改进方案。",
        "该生近期状态松懈，课堂注意力涣散。作业多次未按时提交，已联系家长沟通，仍需持续督促。",
        "学习方法不当，效率较低，投入时间不少但收效甚微。存在畏难情绪，需要重建信心并调整策略。",
        "本学期缺勤较多，实验课缺席导致数据不完整，报告质量欠佳。多次提醒后改善有限，情况令人担忧。",
        "该生情绪波动较大，抗压能力偏弱，遇到挫折容易放弃。学业上出现明显退步，需要心理与学业双重关注。",
        "作业独立性差。已进行批评教育并说明后果，后续需加强学术规范意识。",
        "目标模糊，缺乏规划，时间管理混乱。本学期多项任务未完成，整体状态不佳，亟需引导。",
        "课堂纪律意识薄弱，多次影响他人。学习基础薄弱且不愿请教，进步缓慢，需要加强督促与陪伴。",
        "该生对专业缺乏兴趣，被动应付，缺乏内在动力。建议结合生涯规划重新梳理方向，否则难以持续。"
    };

    /** 审核意见池：≥15 种不同表述，避免"十八条一模一样"的形式主义 */
    private static final String[] OPINIONS = {
        "内容规范，表述完整，予以通过并发布。",
        "点评具体、有事实依据，同意发布。",
        "意见中肯，改进建议可操作，予以发布。",
        "表述客观，符合学业指导要求，通过。",
        "内容与指导主题一致，同意向学生发布。",
        "分析到位，建议明确，通过并发布。",
        "语言得体，指向清晰，准予发布。",
        "评价有据，未涉及敏感表述，通过。",
        "内容完整，建议具体，予以发布。",
        "点评贴合学生实际，同意发布。",
        "结构清楚，建议可执行，通过。",
        "表述准确，无夸大表述，准予发布。",
        "指导意见具体，具备参考价值，通过。",
        "内容合规，逻辑清楚，同意发布。",
        "评价客观中立，建议明确，予以发布。",
        "已核对内容真实性与规范性，通过。",
        "点评有针对性，符合发布标准，同意。",
        "表述规范，建议可落地，准予发布。"
    };

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long userCount = userMapper.selectCount(null);
        if (userCount != null && userCount > 0) {
            log.info("检测到已有数据（{} 个账号），跳过种子数据初始化。", userCount);
            return;
        }
        log.info("开始初始化演示数据……");

        // ---------------- 组织 ----------------
        Major major = new Major();
        major.setCollege("计算机学院");
        major.setName("计算机科学与技术");
        major.setCode("CS");
        majorMapper.insert(major);

        String[] clazzNames = {"计科2201", "计科2205", "计科2207", "计科2208"};
        List<Clazz> clazzes = new ArrayList<>();
        for (String n : clazzNames) {
            Clazz c = new Clazz();
            c.setMajorId(major.getId());
            c.setName(n);
            c.setGrade(2022);
            clazzMapper.insert(c);
            clazzes.add(c);
        }

        // ---------------- 账号 ----------------
        userMapper.insert(newUser("admin", "王主任", "ADMIN"));
        userMapper.insert(newUser("admin02", "李教务", "ADMIN"));

        Teacher t1 = newTeacher("teacher01", "张明远", "T001", "副教授");
        Teacher t2 = newTeacher("teacher02", "李静", "T002", "讲师");
        Teacher t3 = newTeacher("teacher03", "王海涛", "T003", "教授");

        // 学生：2201(6) 2203(2) 2204(2) 2205(2) 2207(4) = 16
        String[][] def = {
            {"S2021001", "王小明", "0"}, {"S2021002", "陈思远", "0"},
            {"S2021003", "刘雨桐", "1"}, {"S2021004", "赵子豪", "0"},
            {"S2021005", "孙嘉怡", "1"}, {"S2021006", "周文博", "0"},
            {"S2025001", "许志远", "0"}, {"S2025002", "林晓彤", "1"},
            {"S2027001", "罗一鸣", "0"}, {"S2027002", "叶清和", "1"},
            {"S2028001", "程子墨", "0"}, {"S2028002", "方婉宁", "1"}
        };
        int[] clazzOf = {0, 0, 0, 0, 0, 0, 1, 1, 2, 2, 3, 3};
        List<Student> students = new ArrayList<>();
        for (int i = 0; i < def.length; i++) {
            User u = newUser(String.format("student%02d", i + 1), def[i][1], "STUDENT");
            userMapper.insert(u);
            Student s = new Student();
            s.setUserId(u.getId());
            s.setStudentNo(def[i][0]);
            s.setName(def[i][1]);
            s.setClazzId(clazzes.get(clazzOf[i]).getId());
            s.setGender(Integer.parseInt(def[i][2]));
            s.setStatus(1);
            studentMapper.insert(s);
            students.add(s);
        }

        // ---------------- 指导关系 ----------------
        // 张明远 → 2201(6)；李静 → 2205(2)；王海涛 → 2207(2)+2208(2)
        bind(t1.getId(), students, 0, 6);
        bind(t2.getId(), students, 6, 8);
        bind(t3.getId(), students, 8, 12);

        // ---------------- 已发布点评 ----------------
        // 条数不手写：由 seed() 返回值累加，最终以启动日志为准（避免多一个真源）
        int published = 0;

        // 计科2201（演示主体）：跨 5 个月自然波动 8/11/7/9/5，**每月均 >=5，不得挖洞**
        // 主班回答"数据健康时是什么样"，不承担任何测试形状；
        // 最后一个月 5 条中有 2 条落在近 7 天，保证趋势图右端贴近当前。
        published +=
                seed(
                        t1,
                        students.subList(0, 6),
                        new int[] {8, 11, 7, 9, 5},
                        new int[] {5, 4, 3, 2, 1},
                        true,
                        0);

        // 计科2205（阈值点 n=5）：单月集中，三图全画、桶级可下钻
        published += seed(t2, students.subList(6, 8), new int[] {5}, new int[] {2}, false, 0);

        // 计科2207（下边界 n=4）：单月集中，三图全空
        // 唯一作用是钉下边界（拦住 n<4 这类错值），界面表现与 n=3 相同属预期，**禁止删除**
        published += seed(t3, students.subList(8, 10), new int[] {4}, new int[] {2}, false, 0);

        // 计科2208（桶级门禁探针）：6 + 1，前月 6 条实心可下钻、后月 1 条空心禁用
        published +=
                seed(t3, students.subList(10, 12), new int[] {6, 1}, new int[] {3, 2}, false, 0);

        // ---------------- 其它状态 ----------------
        int pending = seedOther(t1, students, CommentStatus.PENDING, 8, 1);
        int returned = seedOther(t1, students, CommentStatus.RETURNED, 4, 2);
        int rejected = seedOther(t1, students, CommentStatus.REJECTED, 2, 3);

        log.info(
                "种子数据完成：账号 {} 个，学生 {} 名，点评 已发布{} / 待审核{} / 退回{} / 驳回{}",
                userMapper.selectCount(null),
                students.size(),
                published,
                pending,
                returned,
                rejected);

        var ret = analysisService.rebuildAll();
        log.info("初始分析完成：{}", ret);
    }

    private User newUser(String username, String realName, String role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
        u.setRealName(realName);
        u.setRole(role);
        u.setStatus(1);
        u.setCreatedAt(LocalDateTime.now());
        return u;
    }

    private Teacher newTeacher(String username, String name, String no, String title) {
        User u = newUser(username, name, "TEACHER");
        userMapper.insert(u);
        Teacher t = new Teacher();
        t.setUserId(u.getId());
        t.setTeacherNo(no);
        t.setName(name);
        t.setTitle(title);
        t.setCollege("计算机学院");
        t.setStatus(1);
        teacherMapper.insert(t);
        return t;
    }

    private void bind(Long teacherId, List<Student> all, int from, int to) {
        for (int i = from; i < to; i++) {
            TeacherStudent rel = new TeacherStudent();
            rel.setTeacherId(teacherId);
            rel.setStudentId(all.get(i).getId());
            rel.setRelationType("学业导师");
            rel.setStartTerm("2025-2026-1");
            rel.setStatus(1);
            rel.setCreatedAt(LocalDateTime.now().minusMonths(4));
            teacherStudentMapper.insert(rel);
        }
    }

    /**
     * @param counts 每个月的条数
     * @param monthAgo 对应月份偏移
     * @param firstOneOnly 第一个学生只放 1 条（造 n=1 极端样本）
     */
    private int seed(
            Teacher teacher,
            List<Student> group,
            int[] counts,
            int[] monthAgo,
            boolean firstOneOnly,
            int recentDays) {
        int created = 0;
        int seq = 0;
        for (int b = 0; b < counts.length; b++) {
            YearMonth ym = YearMonth.now().minusMonths(monthAgo[b]);
            for (int k = 0; k < counts[b]; k++) {
                Student stu;
                if (firstOneOnly && seq > 0) {
                    stu = group.get(1 + (seq - 1) % (group.size() - 1));
                } else {
                    stu = group.get(seq % group.size());
                }

                String[] pool = pickPool(seq);
                String content = pool[seq % pool.length];
                if (seq % 3 == 0) {
                    content = content + " " + pool[(seq + 5) % pool.length];
                }

                LocalDateTime publishedAt;
                boolean lastBucket = (b == counts.length - 1);
                if (lastBucket && recentDays > 0 && k >= counts[b] - recentDays) {
                    // 最近 recentDays 天：保证趋势图右端贴近当前时间
                    publishedAt =
                            LocalDateTime.now()
                                    .minusDays(recentDays - (k - (counts[b] - recentDays)))
                                    .withHour(15);
                } else {
                    int day = 3 + (k * 24) / Math.max(1, counts[b]);
                    publishedAt = ym.atDay(Math.min(day, ym.lengthOfMonth())).atTime(10, 30);
                }
                if (publishedAt.isAfter(LocalDateTime.now())) {
                    publishedAt = LocalDateTime.now().minusDays(1);
                }

                Comment c = new Comment();
                c.setTeacherId(teacher.getId());
                c.setStudentId(stu.getId());
                c.setTerm(termOf(publishedAt));
                c.setTopic(TOPICS[seq % TOPICS.length]);
                c.setContent(content);
                c.setStatus(CommentStatus.APPROVED);
                c.setVersion(1);
                c.setSubmitAt(publishedAt.minusDays(2));
                c.setPublishedAt(publishedAt);
                c.setCreatedAt(publishedAt.minusDays(2));
                c.setUpdatedAt(publishedAt);
                commentMapper.insert(c);

                insertEvent(
                        c.getId(),
                        CommentStatus.APPROVED,
                        AuditService.ACT_APPROVE_PUBLISH,
                        OPINIONS[seq % OPINIONS.length],
                        null,
                        1,
                        publishedAt.minusDays(1),
                        "王主任");
                created++;
                seq++;
            }
        }
        return created;
    }

    private int seedOther(
            Teacher teacher, List<Student> all, String status, int count, int variant) {
        int created = 0;
        for (int i = 0; i < count; i++) {
            Student stu = all.get((i + variant) % all.size());
            String[] pool = variant == 1 ? NEU : (variant == 2 ? NEG : POS);
            String content = pool[i % pool.length];

            Comment c = new Comment();
            c.setTeacherId(teacher.getId());
            c.setStudentId(stu.getId());
            c.setTerm("2025-2026-1");
            c.setTopic(TOPICS[i % TOPICS.length]);
            c.setContent(content);
            c.setStatus(status);
            c.setVersion(CommentStatus.RETURNED.equals(status) ? 2 : 1);
            c.setCreatedAt(LocalDateTime.now().minusDays(12 + i * 2L));
            c.setSubmitAt(LocalDateTime.now().minusDays(9 + i * 2L));
            c.setUpdatedAt(LocalDateTime.now().minusDays(6 + i * 2L));
            commentMapper.insert(c);

            insertEvent(
                    c.getId(),
                    "SUBMITTED",
                    AuditService.ACT_SUBMIT,
                    null,
                    null,
                    1,
                    c.getCreatedAt().plusHours(1),
                    "张明远");

            if (!CommentStatus.PENDING.equals(status)) {
                insertEvent(
                        c.getId(),
                        status,
                        CommentStatus.RETURNED.equals(status)
                                ? AuditService.ACT_RETURN
                                : AuditService.ACT_REJECT,
                        CommentStatus.RETURNED.equals(status)
                                ? "点评内容偏笼统，缺少具体事例支撑，请补充本学期的具体表现与改进建议后重新提交。"
                                : "内容涉及未经核实的表述，且与学业指导主题关联度不足，本次不予通过，请核实后重新提交。",
                        content,
                        1,
                        LocalDateTime.now().minusDays(7 + i * 2L),
                        "王主任");
            }
            created++;
        }
        return created;
    }

    private void insertEvent(
            Long commentId,
            String result,
            String actionType,
            String opinion,
            String beforeContent,
            Integer round,
            LocalDateTime at,
            String auditor) {
        CommentAudit a = new CommentAudit();
        a.setCommentId(commentId);
        a.setAuditorId(1L);
        a.setAuditorName(auditor);
        a.setResult(result);
        a.setActionType(actionType);
        a.setOpinion(opinion);
        a.setBeforeContent(beforeContent);
        a.setRound(round);
        a.setAuditAt(at);
        auditMapper.insert(a);
    }

    private String[] pickPool(int i) {
        int m = i % 10;
        if (m < 6) {
            return POS;
        }
        if (m < 9) {
            return NEU;
        }
        return NEG;
    }

    private String termOf(LocalDateTime t) {
        int month = t.getMonthValue();
        int year = t.getYear();
        if (month >= 9) {
            return year + "-" + (year + 1) + "-1";
        }
        if (month <= 1) {
            return (year - 1) + "-" + year + "-1";
        }
        return (year - 1) + "-" + year + "-2";
    }

    private static final String[] TOPICS = {
        "学业规划", "课程学习", "科研训练", "竞赛指导", "毕业设计",
        "实习就业", "心理健康", "时间管理", "专业分流", "考研准备"
    };
}
