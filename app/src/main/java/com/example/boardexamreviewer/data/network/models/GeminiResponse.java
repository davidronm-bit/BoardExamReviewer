package com.example.boardexamreviewer.data.network.models;

import java.util.List;

public class GeminiResponse {
    public List<Candidate> candidates;

    public static class Candidate {
        public GeminiRequest.Content content;
    }
}
