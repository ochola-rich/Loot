package com.loot.controller.payment;

import com.loot.domain.model.EntryPayment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "reference", source = "mpesaRef")
    PaymentResponse toResponse(EntryPayment payment);
}
