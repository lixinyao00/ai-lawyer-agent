package com.ruoyi.lawyer.retrieval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 本地法律语义特征向量编码器
 *
 * <p>当远端 Embedding 网关不可用（未配置 / 403 embeddings_disabled / 4xx / 5xx / 超时）时，
 * 作为确定性降级实现使用。编码流程：</p>
 * <ol>
 *   <li>法律同义概念映射：把口语表达（房东、辞退、押金、借钱……）展开为法言法语概念；</li>
 *   <li>特征抽取：中文单字、中文二元组、英文/数字整词 + 概念词项；</li>
 *   <li>确定性双哈希特征投影（Feature Hashing）：投影到固定维度稠密空间；</li>
 *   <li>L2 归一化，使点积即余弦相似度。</li>
 * </ol>
 *
 * <p>纯函数、无随机数、跨进程与跨重启 100% 可复现，可离线单测。</p>
 *
 * @author ruoyi
 */
@Component
public class LocalSemanticEmbeddingEncoder
{
    /** 默认向量维度 */
    public static final int DEFAULT_DIMENSIONS = 256;

    /** 概念词项权重 */
    private static final double CONCEPT_WEIGHT = 3.0;

    /** 中文单字权重 */
    private static final double UNIGRAM_WEIGHT = 0.5;

    /** 中文二元组权重 */
    private static final double BIGRAM_WEIGHT = 1.0;

    /** 英文/数字整词权重 */
    private static final double WORD_WEIGHT = 1.2;

    /**
     * 法律同义概念映射表：口语/同义词 {@code ->} 规范法言法语概念（空格分隔）。
     * 覆盖劳动、租赁、借贷、合同、侵权五类核心场景。
     */
    private static final List<Concept> CONCEPTS = List.of(
            // 劳动
            new Concept(new String[] { "辞退", "开除", "解雇", "炒鱿鱼", "裁员" },
                    "解除劳动合同 违法解除 经济补偿 赔偿金"),
            new Concept(new String[] { "老板", "单位", "公司", "企业", "用工方" },
                    "用人单位 劳动关系"),
            new Concept(new String[] { "打工", "上班", "入职", "员工", "职工", "劳动者" },
                    "劳动者 劳动关系 劳动合同"),
            new Concept(new String[] { "工资", "月薪", "薪资", "薪水" },
                    "工资 劳动报酬"),
            new Concept(new String[] { "赔偿", "补偿", "赔钱" },
                    "赔偿金 经济补偿"),
            new Concept(new String[] { "试用期" },
                    "试用期 劳动合同期限"),
            new Concept(new String[] { "签合同", "未签合同", "没签合同", "书面合同" },
                    "订立书面劳动合同 二倍工资"),
            new Concept(new String[] { "加班", "加班费", "熬夜" },
                    "延长工作时间 加班费 劳动报酬"),
            new Concept(new String[] { "年假", "带薪休假" },
                    "年休假 带薪年休假"),
            new Concept(new String[] { "工伤", "受伤", "工伤认定" },
                    "工伤 工伤保险 职业病"),
            // 租赁
            new Concept(new String[] { "房东", "房主", "出租方" },
                    "出租人 房屋租赁 租赁合同"),
            new Concept(new String[] { "租客", "房客", "承租人", "租房人" },
                    "承租人 房屋租赁 租赁合同"),
            new Concept(new String[] { "租房", "房屋租赁", "出租", "承租" },
                    "房屋租赁 租赁合同 出租人 承租人"),
            new Concept(new String[] { "押金", "保证金" },
                    "押金 保证金 租赁物 返还"),
            new Concept(new String[] { "定金" },
                    "定金 担保 违约金"),
            new Concept(new String[] { "租金", "房租" },
                    "租金 租赁合同 支付"),
            new Concept(new String[] { "退租", "搬走", "退房" },
                    "解除租赁合同 返还租赁物"),
            new Concept(new String[] { "不退", "拒不退还", "扣着", "扣留" },
                    "拒绝返还 返还 违约责任"),
            new Concept(new String[] { "转租" },
                    "转租 承租人 出租人同意"),
            new Concept(new String[] { "优先购买", "优先买", "卖房" },
                    "承租人优先购买权 房屋出卖"),
            // 借贷
            new Concept(new String[] { "借钱", "借款", "欠钱", "还钱", "借我", "借了" },
                    "借款合同 借款人 贷款人 返还借款"),
            new Concept(new String[] { "利息", "利率", "利滚利" },
                    "利息 借款利率 LPR 四倍"),
            new Concept(new String[] { "逾期", "到期不还", "不还钱", "赖账" },
                    "逾期还款 违约责任 返还借款"),
            new Concept(new String[] { "借条", "欠条" },
                    "借款合同 债权凭证"),
            // 合同
            new Concept(new String[] { "违约", "违约金", "毁约" },
                    "违约责任 违约金 损失赔偿"),
            new Concept(new String[] { "霸王条款", "格式条款", "免责条款" },
                    "格式条款 免除责任 无效"),
            new Concept(new String[] { "定金罚则" },
                    "定金 双倍返还 违约金"),
            new Concept(new String[] { "合同", "协议" },
                    "合同 协议 订立 履行"),
            // 侵权
            new Concept(new String[] { "撞人", "车祸", "交通事故", "撞车" },
                    "机动车交通事故责任 侵权 损害赔偿"),
            new Concept(new String[] { "打人", "受伤", "人身损害", "精神损失" },
                    "侵权责任 人身损害 精神损害赔偿"),
            new Concept(new String[] { "侵权", "损害" },
                    "侵权责任 损害赔偿"),
            new Concept(new String[] { "诉讼时效", "过期", "时效" },
                    "诉讼时效 期间 三年"));

    private final int dimensions;

    public LocalSemanticEmbeddingEncoder()
    {
        this(DEFAULT_DIMENSIONS);
    }

    public LocalSemanticEmbeddingEncoder(int dimensions)
    {
        this.dimensions = dimensions > 0 ? dimensions : DEFAULT_DIMENSIONS;
    }

    /**
     * 当前编码器维度
     *
     * @return 维度
     */
    public int getDimensions()
    {
        return dimensions;
    }

    /**
     * 本地编码器模型标识，如 local-legal-hash-256
     *
     * @return 模型标识
     */
    public String modelName()
    {
        return "local-legal-hash-" + dimensions;
    }

    /**
     * 单条文本编码为 L2 归一化向量
     *
     * @param text 文本
     * @return 定长归一化向量（空文本返回全零向量）
     */
    public float[] encode(String text)
    {
        float[] vector = new float[dimensions];
        Map<String, Double> features = extractFeatures(text);
        for (Map.Entry<String, Double> entry : features.entrySet())
        {
            double weight = entry.getValue();
            if (weight <= 0.0)
            {
                continue;
            }
            long hash = fnv1a64(entry.getKey());
            int index = (int) Math.floorMod(hash, dimensions);
            long signHash = splitMix64(hash);
            double sign = (signHash & 1L) == 0L ? 1.0 : -1.0;
            vector[index] += (float) (sign * weight);
        }
        normalize(vector);
        return vector;
    }

    /**
     * 批量编码，按输入顺序返回
     *
     * @param texts 文本集合
     * @return 向量集合
     */
    public List<float[]> encodeBatch(List<String> texts)
    {
        List<float[]> vectors = new ArrayList<>();
        if (texts == null)
        {
            return vectors;
        }
        for (String text : texts)
        {
            vectors.add(encode(text));
        }
        return vectors;
    }

    /**
     * 抽取文本特征权重：概念词项 + 单字 + 二元组 + 英文/数字整词
     */
    private Map<String, Double> extractFeatures(String text)
    {
        Map<String, Double> features = new LinkedHashMap<>();
        String source = text == null ? "" : text.trim().toLowerCase();
        if (source.isEmpty())
        {
            return features;
        }

        // 1) 法律同义概念映射展开
        for (Concept concept : CONCEPTS)
        {
            for (String trigger : concept.triggers)
            {
                if (source.contains(trigger))
                {
                    for (String term : concept.canonicalTerms)
                    {
                        features.merge(term, CONCEPT_WEIGHT, Double::sum);
                    }
                }
            }
        }

        // 2) 原始文本字符 n-gram 特征
        StringBuilder chineseRun = new StringBuilder();
        StringBuilder latinRun = new StringBuilder();
        for (int i = 0; i < source.length(); i++)
        {
            char ch = source.charAt(i);
            if (isHan(ch))
            {
                flushLatin(features, latinRun);
                features.merge(String.valueOf(ch), UNIGRAM_WEIGHT, Double::sum);
                chineseRun.append(ch);
            }
            else
            {
                flushChineseBigrams(features, chineseRun);
                if (Character.isLetterOrDigit(ch))
                {
                    latinRun.append(ch);
                }
                else
                {
                    flushLatin(features, latinRun);
                }
            }
        }
        flushChineseBigrams(features, chineseRun);
        flushLatin(features, latinRun);
        return features;
    }

    private void flushChineseBigrams(Map<String, Double> features, StringBuilder run)
    {
        if (run.length() >= 2)
        {
            for (int i = 0; i + 1 < run.length(); i++)
            {
                String bigram = run.substring(i, i + 2);
                features.merge(bigram, BIGRAM_WEIGHT, Double::sum);
            }
        }
        run.setLength(0);
    }

    private void flushLatin(Map<String, Double> features, StringBuilder run)
    {
        if (run.length() > 0)
        {
            features.merge(run.toString(), WORD_WEIGHT, Double::sum);
            run.setLength(0);
        }
    }

    private boolean isHan(char ch)
    {
        return ch >= '\u4e00' && ch <= '\u9fa5';
    }

    private void normalize(float[] vector)
    {
        double sum = 0.0;
        for (float value : vector)
        {
            sum += (double) value * value;
        }
        if (sum <= 0.0)
        {
            return;
        }
        double norm = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++)
        {
            vector[i] = (float) (vector[i] / norm);
        }
    }

    private static long fnv1a64(String value)
    {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++)
        {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long splitMix64(long value)
    {
        long z = value + 0x9e3779b97f4a7c15L;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static final class Concept
    {
        private final String[] triggers;

        private final String[] canonicalTerms;

        private Concept(String[] triggers, String canonical)
        {
            this.triggers = triggers;
            this.canonicalTerms = canonical.split(" ");
        }
    }
}
