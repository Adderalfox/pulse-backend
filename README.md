# Pulse: AI-Driven Employee Recognition & Award Recommendation System

Pulse is a backend system built with the Play Framework (Scala) that uses Large Language Models (LLMs) and Vector Databases (Qdrant) to transform peer appreciations into actionable professional insights and automated award nominations.

---

## 🚀 Core Architecture & Workflow

The system operates in three main phases: **Intelligence Pipeline**, **Profile Mapping**, and **Award Recommendation**.

### 1. Appreciation Creation & Intelligence Pipeline
When an employee sends an appreciation, the following sequence occurs:

1.  **Controller:** `AppreciationController.createAppreciation` receives the request.
2.  **Service:** `AppreciationService` saves the raw text and metadata to **PostgreSQL**.
3.  **Pipeline (Async):** `IntelligencePipeline.process` is triggered.
    *   **LLM Call 1 (Skill Extraction):** `SkillExtractionService` calls Gemini to identify skills and confidence scores from the text.
    *   **LLM Call 2 (Embedding):** `EmbeddingService` sends the "Skills + Message" to Gemini's embedding model to get a 768-dimension vector.
    *   **Vector Storage:** The embedding is stored in the `appreciations` collection in **Qdrant** (used as evidence for later).

### 2. Employee Profile Mapping (The Rolling Centroid)
After an appreciation is embedded, the system updates the recipient's identity:

1.  **Method:** `EmbeddingService.updateEmployeeProfileVector`
2.  **Logic:** It fetches the existing "Profile Vector" from the `employee_profiles` collection in Qdrant.
3.  **Calculation:** It computes a **Rolling Centroid**:
    `New Profile = ((Old Profile * Previous Count) + New Appreciation Vector) / Total Count`
4.  **Storage:** The updated centroid is saved back to Qdrant. This vector represents the employee's "Professional Footprint."

### 3. Award Recommendation & RAG
When a manager wants to see which awards an employee deserves:

1.  **Method:** `AwardRecommendationService.recommendAwards`
2.  **Vector Search:** The system takes the employee's **Profile Centroid** and searches the `award_definitions` collection in Qdrant using Cosine Similarity.
3.  **Evidence Retrieval (RAG):** For the top matching awards, it searches the user's *individual* past appreciations to find "Evidence" (messages most similar to the award criteria).
4.  **LLM Call 3 (Justification):** Gemini receives the Award Criteria + Evidence and generates a 1-2 sentence justification for the nomination.

---

## LLM (Gemini) Integration Points

| Service | Task | Model/Type |
| :--- | :--- | :--- |
| `SkillExtractionService` | Extracting skills from text | `generateContent` (LLM) |
| `EmbeddingService` | Converting text to vectors | `embedText` (Embedding) |
| `AwardRecommendationService` | Writing match justifications | `generateContent` (LLM) |
---

## 🛠 Tech Stack

*   **Language:** Scala 2.13
*   **Framework:** Play Framework
*   **Primary Database:** PostgreSQL (Appreciations, Users, Metadata)
*   **Vector Database:** Qdrant (Embeddings, Profiles, Award Definitions)
*   **AI:** Google Gemini (Generative AI & Embeddings)
*   **Cache:** Redis (Interaction tracking)

---

## 📂 Key File Map

*   `app/services/IntelligencePipeline.scala`: The "brain" that connects creation to AI analysis.
*   `app/services/EmbeddingService.scala`: Manages vector math and Qdrant updates.
*   `app/services/AwardRecommendationService.scala`: Handles the similarity search for awards.
*   `app/services/NominationService.scala`: Handles the RAG-based nomination drafting.
*   `app/utils/QdrantClientWrapper.scala`: Low-level vector DB operations.
