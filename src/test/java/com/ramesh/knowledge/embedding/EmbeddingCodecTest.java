package com.ramesh.knowledge.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingCodecTest {

    @Test
    void roundTripsVectorsWithFiveDecimalPrecision() {
        float[] original = {0.1234567f, -0.7654321f, 0f, 1f};

        float[] decoded = EmbeddingCodec.decode(EmbeddingCodec.encode(original));

        assertThat(decoded).containsExactly(0.12346f, -0.76543f, 0f, 1f);
    }

    @Test
    void handlesEmptyVector() {
        assertThat(EmbeddingCodec.decode(EmbeddingCodec.encode(new float[0]))).isEmpty();
    }

    @Test
    void rejectsMalformedPayloads() {
        assertThatThrownBy(() -> EmbeddingCodec.decode("0.1,0.2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON array");
        assertThatThrownBy(() -> EmbeddingCodec.decode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
