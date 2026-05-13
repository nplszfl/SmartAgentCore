package com.agent.core.memory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 对话记忆实现 - 基于队列
 */
public class ConversationMemory implements Memory {

    private final int maxSize;
    private final ConcurrentLinkedQueue<Message> messages;

    public ConversationMemory() {
        this(100);
    }

    public ConversationMemory(int maxSize) {
        this.maxSize = maxSize;
        this.messages = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void addMessage(Message message) {
        messages.add(message);
        trim();
    }

    @Override
    public List<Message> getMessages() {
        return new ArrayList<>(messages);
    }

    @Override
    public List<Message> getRecentMessages(int count) {
        List<Message> all = new ArrayList<>(messages);
        if (all.size() <= count) {
            return all;
        }
        return all.subList(all.size() - count, all.size());
    }

    @Override
    public void clear() {
        messages.clear();
    }

    private void trim() {
        if (maxSize > 0 && messages.size() > maxSize) {
            List<Message> list = new ArrayList<>(messages);
            messages.clear();
            int start = list.size() - maxSize;
            for (int i = Math.max(0, start); i < list.size(); i++) {
                messages.add(list.get(i));
            }
        }
    }

    public int size() {
        return messages.size();
    }
}
