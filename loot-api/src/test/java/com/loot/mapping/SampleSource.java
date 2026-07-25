package com.loot.mapping;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
class SampleSource {
    private String name;
    private int amount;
}
