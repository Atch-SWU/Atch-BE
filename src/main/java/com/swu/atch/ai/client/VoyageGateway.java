package com.swu.atch.ai.client;

import java.util.List;

public interface VoyageGateway {
    boolean enabled();

    String model();

    List<float[]> embed(List<String> texts, String inputType);
}
