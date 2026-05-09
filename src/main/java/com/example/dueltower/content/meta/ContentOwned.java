package com.example.dueltower.content.meta;

public interface ContentOwned {
    default String contentOwner() {
        return ContentOwnerIds.COMMON;
    }
}
