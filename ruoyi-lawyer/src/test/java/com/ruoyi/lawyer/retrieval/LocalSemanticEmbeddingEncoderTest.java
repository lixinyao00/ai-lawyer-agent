package com.ruoyi.lawyer.retrieval;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * LocalSemanticEmbeddingEncoder 单元测试
 *
 * @author ruoyi
 */
class LocalSemanticEmbeddingEncoderTest
{
    private static final String LEASE_ARTICLE = "租赁合同是出租人将租赁物交付承租人使用、收益，承租人支付租金的合同。"
            + "承租人应当妥善保管租赁物，租赁期限届满，承租人应当返还租赁物。";

    private static final String LABOR_ARTICLE = "经济补偿按劳动者在本单位工作的年限，每满一年支付一个月工资的标准向劳动者支付。"
            + "用人单位违反本法规定解除或者终止劳动合同的，应当支付赔偿金。";

    private static final String LOAN_ARTICLE = "借款合同是借款人向贷款人借款，到期返还借款并支付利息的合同。"
            + "禁止高利放贷，借款的利率不得违反国家有关规定。";

    @Test
    void defaultDimensionsIs256AndVectorIsNormalized()
    {
        LocalSemanticEmbeddingEncoder encoder = new LocalSemanticEmbeddingEncoder();
        float[] vector = encoder.encode("房东不退押金怎么办");

        assertEquals(256, vector.length);
        assertEquals(256, encoder.getDimensions());
        assertEquals("local-legal-hash-256", encoder.modelName());
        assertEquals(1.0, norm(vector), 1e-5);
    }

    @Test
    void customDimensionsAreRespected()
    {
        LocalSemanticEmbeddingEncoder encoder = new LocalSemanticEmbeddingEncoder(64);
        float[] vector = encoder.encode("劳动合同纠纷");

        assertEquals(64, vector.length);
        assertEquals("local-legal-hash-64", encoder.modelName());
        assertEquals(1.0, norm(vector), 1e-5);
    }

    @Test
    void encodingIsDeterministicAcrossInstances()
    {
        String text = "公司违法辞退员工，经济补偿怎么算";
        float[] first = new LocalSemanticEmbeddingEncoder(128).encode(text);
        float[] second = new LocalSemanticEmbeddingEncoder(128).encode(text);

        assertArrayEquals(first, second);
    }

    @Test
    void emptyTextProducesZeroVector()
    {
        float[] vector = new LocalSemanticEmbeddingEncoder(32).encode("  ");

        assertEquals(32, vector.length);
        assertEquals(0.0, norm(vector), 1e-6);
    }

    @Test
    void leaseColloquialQueryIsCloserToLeaseArticleThanLaborArticle()
    {
        LocalSemanticEmbeddingEncoder encoder = new LocalSemanticEmbeddingEncoder(256);
        float[] query = encoder.encode("房东不退押金还扣着租金怎么办");

        double leaseScore = cosine(query, encoder.encode(LEASE_ARTICLE));
        double laborScore = cosine(query, encoder.encode(LABOR_ARTICLE));

        assertTrue(leaseScore > laborScore,
                "租金/押金口语应更接近租赁条文, lease=" + leaseScore + ", labor=" + laborScore);
        assertTrue(leaseScore > 0.15);
    }

    @Test
    void laborColloquialQueryIsCloserToLaborArticleThanLeaseArticle()
    {
        LocalSemanticEmbeddingEncoder encoder = new LocalSemanticEmbeddingEncoder(256);
        float[] query = encoder.encode("老板辞退我，工作三年能拿多少赔偿");

        double laborScore = cosine(query, encoder.encode(LABOR_ARTICLE));
        double leaseScore = cosine(query, encoder.encode(LEASE_ARTICLE));

        assertTrue(laborScore > leaseScore,
                "辞退/赔偿口语应更接近劳动条文, labor=" + laborScore + ", lease=" + leaseScore);
    }

    @Test
    void loanColloquialQueryIsCloserToLoanArticle()
    {
        LocalSemanticEmbeddingEncoder encoder = new LocalSemanticEmbeddingEncoder(256);
        float[] query = encoder.encode("朋友借钱到期不还，利息怎么算");

        double loanScore = cosine(query, encoder.encode(LOAN_ARTICLE));
        double laborScore = cosine(query, encoder.encode(LABOR_ARTICLE));

        assertTrue(loanScore > laborScore,
                "借钱/利息口语应更接近借贷条文, loan=" + loanScore + ", labor=" + laborScore);
    }

    @Test
    void batchEncodingPreservesOrderAndSize()
    {
        LocalSemanticEmbeddingEncoder encoder = new LocalSemanticEmbeddingEncoder(32);
        List<float[]> vectors = encoder.encodeBatch(List.of("押金", "借款利息", "交通事故"));

        assertEquals(3, vectors.size());
        assertArrayEquals(encoder.encode("押金"), vectors.get(0));
        assertArrayEquals(encoder.encode("借款利息"), vectors.get(1));
        assertArrayEquals(encoder.encode("交通事故"), vectors.get(2));
    }

    private double norm(float[] vector)
    {
        double sum = 0.0;
        for (float value : vector)
        {
            sum += (double) value * value;
        }
        return Math.sqrt(sum);
    }

    private double cosine(float[] left, float[] right)
    {
        double sum = 0.0;
        for (int i = 0; i < left.length; i++)
        {
            sum += (double) left[i] * right[i];
        }
        return sum;
    }
}
