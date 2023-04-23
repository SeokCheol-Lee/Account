package com.example.account.dto;

import com.example.account.type.TransactionResultType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

public class UseBalance {
    @Getter
    @Setter
    @AllArgsConstructor
    public static class Request{
        @NotNull
        @Min(1)
        private Long userId;

        @NotNull
        @Size(min=10, max=10)
        private String accountNumber;

        @NotNull
        @Min(10)
        @Max(1000_000_000)
        private Long amount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private String accountNumber;
        private TransactionResultType transactionResultType;
        private String transactionId;
        private Long amount;
        private LocalDateTime transactedAt;

        public static Response from(TransactionDto transactionDto) {
            return Response.builder()
                    .accountNumber(transactionDto.getAccountNumber())
                    .transactionResultType(transactionDto.getTransactionResultType())
                    .transactionId(transactionDto.getTransactionId())
                    .amount(transactionDto.getAmount())
                    .transactedAt(transactionDto.getTransactedAt())
                    .build();

        }
    }
}
