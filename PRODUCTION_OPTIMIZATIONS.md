# Production Optimization & Scalability Roadmap

This document outlines the potential challenges, bottlenecks, and relevancy issues that may arise as the Pulse platform scales to thousands of users and millions of appreciations.

## 1. Mathematical & Relevancy Optimizations

### 1.1 The "Diluted Centroid" Problem [SOLVED]
*   **Problem:** The simple rolling average `((old * count) + new) / totalCount` gives equal weight to all appreciations. As an employee's history grows, new appreciations have diminishing impact ($1/N$). The profile becomes "stagnant."
*   **Solution:** **Implemented Exponential Moving Average (EMA)**. Using an $\alpha$ decay factor (configured in `application.conf`), recent professional behavior now maintains a constant impact (e.g., 10%) on the profile vector regardless of the total count of appreciations.

### 1.2 Profile "Smearing" (Generalist Drift)
*   **Problem:** High-performing employees receive appreciations across diverse categories (Leadership, Technical, Soft Skills). Their rolling centroid drifts toward the "center" of the vector space, making it too generic to trigger specific award thresholds.
*   **Solution:** **Multi-Centroid Profiling**. Instead of one vector per user, maintain 3-5 "cluster centroids" representing different professional pillars. Match awards against the strongest individual pillar rather than the total average.

### 1.3 The "Feedback Loop" Bias
*   **Problem:** System recommendations influence manager behavior. If the system recommends "Innovator," managers write "Innovator" awards, which reinforces the "Innovator" vector, creating an echo chamber that ignores new skill growth.
*   **Solution:** **Epsilon-Greedy Exploration**. Reserve a small percentage of recommendations for "Discovery" awards (awards the user is close to but hasn't matched yet) to test for emerging skills.

---

## 2. System Architecture & Scalability

### 2.1 Qdrant Index Latency
*   **Problem:** As the `appreciations` collection grows into millions of points, vector search latency increases.
*   **Solution:**
    *   **Payload Indexing:** Explicitly index `recipient_id` and `company_id` in Qdrant to speed up filtered searches.
    *   **Quantization:** Use Scalar or Product Quantization in Qdrant to reduce memory usage and speed up distance calculations.

### 2.2 Relational DB vs. Vector Store Consistency
*   **Problem:** If the `IntelligencePipeline` fails halfway, the PostgreSQL record might exist but the Qdrant embedding might be missing, leading to "ghost" appreciations that don't affect profiles.
*   **Solution:** **Idempotency & Outbox Pattern**. Use the `EmbeddingSyncLog` to track state. Implement a background worker that retries failed embeddings based on the sync log.

### 2.3 Server Crash & Pipeline Pressure
*   **Problem:** Sudden spikes in appreciations (e.g., during annual reviews) can overwhelm the Play framework's thread pool, leading to dropped requests or crashes.
*   **Solution:** **Message Queuing (Kafka/RabbitMQ)**. Instead of calling the `IntelligencePipeline` directly in a `Future`, push the task to a durable queue. This decouples appreciation creation from heavy AI processing.

---

## 3. LLM Efficiency & Cost Management

### 3.1 Token Usage & Cost Per Appreciation
*   **Problem:** Sending every appreciation to Gemini for Skill Extraction and Embedding is expensive. Long-form appreciations can consume thousands of tokens in justifications.
*   **Solution:**
    *   **Local Embedding Models:** Use a local sidecar container (e.g., `sentence-transformers` via Text-Generation-Inference) for embeddings ($0 cost). Only use Gemini for high-reasoning tasks.
    *   **Semantic Caching:** Store common appreciation phrases and their extracted skills in Redis. If a new appreciation is semantically similar to a cached one, skip the LLM call.

### 3.2 Context Window Limits in Recommendations
*   **Problem:** When generating a justification for a long-tenured employee, fetching "all evidence" might exceed the LLM's context window or become too expensive.
*   **Solution:** **Maximal Marginal Relevance (MMR)**. Select a diverse subset of evidence rather than just the "top similarity" results. This keeps the prompt small, the cost low, and the justification varied.

### 3.3 Rate Limiting & Tier Management
*   **Problem:** Gemini API has strict quotas. High-volume concurrent appreciations will trigger `429 Too Many Requests`.
*   **Solution:** **Request Batching & Exponential Backoff**. Batch multiple skill extraction requests into a single LLM prompt where possible, and implement a robust retry mechanism in `GeminiClient`.

---

## 4. Reliability & Error Handling

### 4.1 Dimension Mismatch on Model Upgrade
*   **Problem:** If you switch from `text-embedding-004` (768d) to a newer model (e.g., 1024d), all existing profile vectors in Qdrant become incompatible.
*   **Solution:** **Versioned Collections**. Always include the model version in the collection name. Maintain a "re-indexing" script that can pull raw text from Postgres and re-generate embeddings for the new model in the background.

### 4.2 "Hallucinated" Justifications
*   **Problem:** The LLM might "invent" achievements for an award nomination if the evidence provided is weak.
*   **Solution:** **Grounding & Guardrails**. Use strict system prompts that forbid using information outside the provided evidence. Implement a secondary "hallucination check" or confidence score for the generated text.
