package com.tpi.toxicity.api;

class TextTooLongException extends RuntimeException {
    TextTooLongException(int maxChars) {
        super("text exceeds the maximum of " + maxChars + " characters");
    }
}
