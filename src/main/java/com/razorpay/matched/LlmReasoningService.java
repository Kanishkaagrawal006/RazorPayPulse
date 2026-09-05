package com.razorpay.matched;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.razorpay.entity.BankStatementLine;
import com.razorpay.entity.SettlementRecord;
import com.razorpay.rules.ReconciliationRules;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class LlmReasoningService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public LlmReasoningService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public LlmMatchResult findMatch(SettlementRecord settlement, List<BankStatementLine> availableLines) {
        if (availableLines == null || availableLines.isEmpty()) {
            return LlmMatchResult.noCandidate();
        }

        List<BankStatementLine> candidates = findCandidates(settlement, availableLines);
        if (candidates.isEmpty()) {
            return LlmMatchResult.noCandidate();
        }

        try {
            String prompt = buildPrompt(settlement, candidates);
            String response = chatClient.prompt().user(prompt).call().content();

            if (response == null || response.isBlank()) {
                return LlmMatchResult.noCandidate();
            }
            return parseResponse(response, candidates);
        } catch (Exception e) {
            System.err.println("Gemini reasoning failed for " + settlement.getSettlementId() + ": " + e.getMessage());
            return LlmMatchResult.noCandidate();
        }
    }

    private List<BankStatementLine> findCandidates(SettlementRecord settlement, List<BankStatementLine> availableLines) {
        BigDecimal settlementAmount = settlement.getNetSettled();
        if (settlementAmount == null || settlement.getSettledAt() == null) {
            return List.of();
        }

        var settlementDate = settlement.getSettledAt().toLocalDate();
        List<BankStatementLine> candidates = new ArrayList<>();

        for (BankStatementLine line : availableLines) {
            if (line.getAmount() == null || line.getValueDate() == null) {
                continue;
            }
            if (settlementAmount.compareTo(line.getAmount()) != 0) {
                continue;
            }

            // NOT Math.abs() - a bank credit before the settlement even
            // happened isn't physically plausible, same convention Pass 1/2 use.
            long dayDifference = ChronoUnit.DAYS.between(settlementDate, line.getValueDate());
            if (dayDifference >= 0 && dayDifference <= ReconciliationRules.LLM_DATE_TOLERANCE_DAYS) {
                candidates.add(line);
            }
        }
        return candidates;
    }

    private String buildPrompt(SettlementRecord settlement, List<BankStatementLine> candidates) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                You are a financial reconciliation assistant.

                Determine whether one of the supplied bank statement
                candidates is a valid match for the settlement.

                Rules:
                - Do not invent information.
                - Only select a bank_line_id supplied below.
                - Exact amount agreement is required.
                - Consider payment references in narration.
                - Consider merchant/company references.
                - Consider date proximity.
                - If evidence is insufficient, return NO_MATCH.
                - If multiple candidates are similarly supported, return AMBIGUOUS.
                - Return ONLY valid JSON.

                Return exactly this structure:

                {
                  "decision": "MATCH",
                  "bank_line_id": 123,
                  "confidence": 0.95,
                  "reasoning": "Brief explanation"
                }

                Valid decisions:
                MATCH
                NO_MATCH
                AMBIGUOUS

                For NO_MATCH or AMBIGUOUS:
                "bank_line_id" must be null.

                Settlement:
                """);

        prompt.append("\nsettlement_id: ").append(settlement.getSettlementId());
        prompt.append("\npayment_id: ").append(settlement.getPaymentId());
        prompt.append("\nnet_settled: ").append(settlement.getNetSettled());
        prompt.append("\nsettled_at: ").append(settlement.getSettledAt());
        prompt.append("\n\nBank candidates:\n");

        for (BankStatementLine line : candidates) {
            long dayDifference = Math.abs(ChronoUnit.DAYS.between(
                    settlement.getSettledAt().toLocalDate(), line.getValueDate()));

            prompt.append("\nCandidate:\n");
            prompt.append("bank_line_id: ").append(line.getLineId()).append("\n");
            prompt.append("narration: ").append(line.getNarration()).append("\n");
            prompt.append("amount: ").append(line.getAmount()).append("\n");
            prompt.append("value_date: ").append(line.getValueDate()).append("\n");
            prompt.append("date_difference_days: ").append(dayDifference).append("\n");
        }
        return prompt.toString();
    }

    private LlmMatchResult parseResponse(String response, List<BankStatementLine> candidates) {
        try {
            String cleaned = cleanJson(response);
            JsonNode json = objectMapper.readTree(cleaned);

            String decision = json.path("decision").asText("NO_MATCH").trim().toUpperCase();
            double confidence = json.path("confidence").asDouble(0.0);
            confidence = Math.max(0.0, Math.min(1.0, confidence));
            String reasoning = json.path("reasoning").asText("No reasoning provided");

            if ("AMBIGUOUS".equals(decision)) {
                return LlmMatchResult.ambiguous(candidates);
            }
            if (!"MATCH".equals(decision)) {
                return LlmMatchResult.noCandidate();
            }
            if (!json.hasNonNull("bank_line_id")) {
                return LlmMatchResult.noCandidate();
            }

            int bankLineId = json.get("bank_line_id").asInt();
            BankStatementLine selected = candidates.stream()
                    .filter(line -> line.getLineId() == bankLineId)
                    .findFirst()
                    .orElse(null);

            if (selected == null) {
                return LlmMatchResult.noCandidate();
            }
            if (confidence < ReconciliationRules.LLM_MATCH_CONFIDENCE_THRESHOLD) {
                return LlmMatchResult.noCandidate();
            }

            return LlmMatchResult.matched(selected, confidence, reasoning);
        } catch (Exception e) {
            System.err.println("Failed to parse Gemini response: " + e.getMessage());
            return LlmMatchResult.noCandidate();
        }
    }

    private String cleanJson(String response) {
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }
}