package com.v1hz.bizagent.dto.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SseResponse {
    private String type;    // "content", "error", "done"
    private String data;
    private String message;
    
    public static SseResponse content(String content) {
        SseResponse msg = new SseResponse();
        msg.setType("content");
        msg.setData(content);
        return msg;
    }
    
    public static SseResponse error(String error) {
        SseResponse msg = new SseResponse();
        msg.setType("error");
        msg.setMessage(error);
        return msg;
    }
    
    public static SseResponse done() {
        SseResponse msg = new SseResponse();
        msg.setType("done");
        return msg;
    }
    
    public String toJson() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}