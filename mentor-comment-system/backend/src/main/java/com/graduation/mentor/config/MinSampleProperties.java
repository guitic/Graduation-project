package com.graduation.mentor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 统计最小样本量阈值 —— 隐私门槛的<b>唯一</b>取值来源。
 *
 * <p><b>为什么只有一个 key，而不是三个</b>： 三个 key 意味着配置<b>允许</b> {@code 5/5/3} 这种不对称状态存在，
 * 而"词云门槛可以比情感门槛低"这件事没有任何正当理由 （2~4 条点评的高频词几乎就是原文关键词，比情感比率更能暴露内容）。 <b>一个允许错误状态存在的配置，迟早会处于错误状态。</b>
 *
 * <p>同一个阈值作用在四处：饼图 / 趋势（图级 + 桶级）/ 词云 / 下钻入口。 答辩现场把本值从 5 改成 6，四个图必须同时变化——这本身就是"没有硬编码"的证明。
 */
@Data
@Component
@ConfigurationProperties(prefix = "analysis")
public class MinSampleProperties {

    /** 隐私门槛：任何占比类输出与明细下钻的最小样本量 */
    private int minSample = 5;
}
