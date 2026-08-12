package com.loot.controller.disbursal;

import com.loot.domain.model.PrizeDisbursal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DisbursalMapper {

    @Mapping(target = "reference", source = "gatewayRef")
    DisbursalResponse toResponse(PrizeDisbursal disbursal);
}
