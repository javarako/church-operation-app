package com.church.operation.dto;

public record MemberImageContent(byte[] bytes, String contentType, String filename) {
}
