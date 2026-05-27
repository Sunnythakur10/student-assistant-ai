package com.studentassistant.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FuzzyRelevanceScorer {

    public enum RelevanceLevel {
        HIGH, MEDIUM, LOW, IRRELEVANT
    }

    public static class ScoredDocument {
        private final Document document;
        private final double similarityScore;
        private final RelevanceLevel relevanceLevel;
        private final double fuzzyMembership;

        public ScoredDocument(Document document, double similarityScore,
                              RelevanceLevel relevanceLevel, double fuzzyMembership) {
            this.document = document;
            this.similarityScore = similarityScore;
            this.relevanceLevel = relevanceLevel;
            this.fuzzyMembership = fuzzyMembership;
        }

        public Document getDocument() {
            return document;
        }

        public double getSimilarityScore() {
            return similarityScore;
        }

        public RelevanceLevel getRelevanceLevel() {
            return relevanceLevel;
        }

        public double getFuzzyMembership() {
            return fuzzyMembership;
        }
    }

    private double highRelevanceMembership(double score) {
        if (score >= 0.85) {
            return 1.0;
        }
        if (score >= 0.70) {
            return (score - 0.70) / (0.85 - 0.70);
        }
        return 0.0;
    }

    private double mediumRelevanceMembership(double score) {
        if (score >= 0.80) {
            return Math.max(0.0, (0.92 - score) / (0.92 - 0.80));
        }
        if (score >= 0.60) {
            return (score - 0.60) / (0.80 - 0.60);
        }
        return 0.0;
    }

    // --- UPDATED METHOD: Continuous Triangular Gradient ---
    private double lowRelevanceMembership(double score) {
        // 1. Out of bounds gets 0.0
        if (score >= 0.70 || score <= 0.30) {
            return 0.0;
        }
        // 2. Right Slope (falling from peak 0.50 down to 0.70)
        if (score > 0.50) {
            return (0.70 - score) / (0.70 - 0.50);
        }
        // 3. Left Slope (rising from 0.30 up to peak 0.50)
        return (score - 0.30) / (0.50 - 0.30);
    }
    // ------------------------------------------------------

    private double irrelevantMembership(double score) {
        if (score <= 0.45) {
            return 1.0;
        }
        if (score <= 0.60) {
            return (0.60 - score) / (0.60 - 0.45);
        }
        return 0.0;
    }

    public RelevanceLevel classify(double score) {
        double high = highRelevanceMembership(score);
        double medium = mediumRelevanceMembership(score);
        double low = lowRelevanceMembership(score);
        double irrelevant = irrelevantMembership(score);

        double best = high;
        RelevanceLevel level = RelevanceLevel.HIGH;

        if (medium > best) {
            best = medium;
            level = RelevanceLevel.MEDIUM;
        }
        if (low > best) {
            best = low;
            level = RelevanceLevel.LOW;
        }
        if (irrelevant > best) {
            level = RelevanceLevel.IRRELEVANT;
        }

        return level;
    }

    public double computeFuzzyMembership(double score) {
        double high = highRelevanceMembership(score);
        double medium = mediumRelevanceMembership(score);
        double low = lowRelevanceMembership(score);
        double irrelevant = irrelevantMembership(score);

        double result = (high * 1.0) + (medium * 0.6) + (low * 0.2) + (irrelevant * 0.0);
        return Math.min(result, 1.0);
    }

    public List<ScoredDocument> scoreAndFilter(List<Document> documents, List<Double> similarityScores) {
        int total = Math.min(documents.size(), similarityScores.size());
        List<ScoredDocument> scored = new ArrayList<>(total);

        for (int i = 0; i < total; i++) {
            Document doc = documents.get(i);
            double similarity = similarityScores.get(i);
            RelevanceLevel level = classify(similarity);
            double fuzzyMembership = computeFuzzyMembership(similarity);

            scored.add(new ScoredDocument(doc, similarity, level, fuzzyMembership));
            log.info("Document scored: relevance={}, similarity={}, fuzzy={}", level, similarity, fuzzyMembership);
        }

        List<ScoredDocument> filtered = scored.stream()
                .filter(sd -> sd.getRelevanceLevel() != RelevanceLevel.IRRELEVANT)
                .sorted(Comparator.comparingDouble(ScoredDocument::getFuzzyMembership).reversed())
                .toList();

        log.info("Fuzzy filter: {}/{} documents passed relevance threshold", filtered.size(), total);
        return filtered;
    }

    public String getRelevanceSummary(List<ScoredDocument> scoredDocs) {
        EnumMap<RelevanceLevel, Integer> counts = new EnumMap<>(RelevanceLevel.class);
        counts.put(RelevanceLevel.HIGH, 0);
        counts.put(RelevanceLevel.MEDIUM, 0);
        counts.put(RelevanceLevel.LOW, 0);

        for (ScoredDocument doc : scoredDocs) {
            if (doc.getRelevanceLevel() == RelevanceLevel.HIGH) {
                counts.put(RelevanceLevel.HIGH, counts.get(RelevanceLevel.HIGH) + 1);
            } else if (doc.getRelevanceLevel() == RelevanceLevel.MEDIUM) {
                counts.put(RelevanceLevel.MEDIUM, counts.get(RelevanceLevel.MEDIUM) + 1);
            } else if (doc.getRelevanceLevel() == RelevanceLevel.LOW) {
                counts.put(RelevanceLevel.LOW, counts.get(RelevanceLevel.LOW) + 1);
            }
        }

        return String.format("Context quality — High: %d, Medium: %d, Low: %d",
                counts.get(RelevanceLevel.HIGH),
                counts.get(RelevanceLevel.MEDIUM),
                counts.get(RelevanceLevel.LOW));
    }
}