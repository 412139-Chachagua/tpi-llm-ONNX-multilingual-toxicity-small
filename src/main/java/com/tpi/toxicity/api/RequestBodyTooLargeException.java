package com.tpi.toxicity.api;

import java.io.IOException;

/** Se superó el tamaño máximo del cuerpo mientras se leía (p. ej. chunked, sin Content-Length). */
class RequestBodyTooLargeException extends IOException {
    RequestBodyTooLargeException() {
        super("request body too large");
    }
}
