package com.graduation.mentor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graduation.mentor.entity.Comment;
import org.apache.ibatis.annotations.Select;

public interface CommentMapper extends BaseMapper<Comment> {

    /**
     * 已发布总数。
     *
     * <p>注意：时间分桶与情感聚合一律在 Service 层用 Java 完成， 不使用 date_format() 之类的 MySQL 方言函数——这样 demo profile 切到
     * H2 也能跑。
     */
    @Select(
            """
            select count(1) from comment
            where status = 'APPROVED' and published_at is not null
            """)
    long countPublished();
}
