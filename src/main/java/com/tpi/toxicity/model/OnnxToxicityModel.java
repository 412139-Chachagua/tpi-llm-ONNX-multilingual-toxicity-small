package com.tpi.toxicity.model;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import ai.onnxruntime.OnnxJavaType;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Sesión ONNX y tokenizer, cargados una sola vez al construir el bean. Si algo falla
 * la aplicación no arranca: nunca se responde con una clasificación inventada.
 */
@Component
public class OnnxToxicityModel implements DisposableBean {

    static final String INPUT_IDS = "input_ids";
    static final String ATTENTION_MASK = "attention_mask";
    static final String OUTPUT = "logits";

    private final OrtEnvironment env;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;

    public OnnxToxicityModel(ToxicityProperties props) {
        this(props.modelPath(), props.tokenizerPath(), props.maxTokens());
    }

    public OnnxToxicityModel(Path modelPath, Path tokenizerPath, int maxTokens) {
        requireReadable(modelPath, "modelo ONNX");
        requireReadable(tokenizerPath, "tokenizer");
        HuggingFaceTokenizer tok = null;
        OrtSession sess = null;
        try {
            tok = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(tokenizerPath)
                    .optTruncation(true)
                    .optMaxLength(maxTokens)
                    .optPadding(false)
                    .optAddSpecialTokens(true)
                    .build();
            this.env = OrtEnvironment.getEnvironment();
            sess = env.createSession(modelPath.toString(), new OrtSession.SessionOptions());
            validateInterface(sess);
        } catch (IOException | OrtException | RuntimeException e) {
            closeQuietly(sess);
            if (tok != null) {
                tok.close();
            }
            throw new ModelUnavailableException("No se pudo cargar el modelo o el tokenizer", e);
        }
        this.session = sess;
        this.tokenizer = tok;
    }

    /** Devuelve los 7 logits crudos del modelo para el texto dado. */
    public float[] logits(String text) {
        Encoding enc = tokenizer.encode(text);
        long[][] ids = {enc.getIds()};
        long[][] mask = {enc.getAttentionMask()};
        try (OnnxTensor idsT = OnnxTensor.createTensor(env, ids);
             OnnxTensor maskT = OnnxTensor.createTensor(env, mask);
             OrtSession.Result result = session.run(Map.of(INPUT_IDS, idsT, ATTENTION_MASK, maskT))) {
            float[][] out = (float[][]) result.get(OUTPUT).orElseThrow(
                    () -> new InferenceException("El modelo no devolvió la salida esperada")).getValue();
            if (out.length != 1 || out[0].length != ToxicityCategory.values().length) {
                throw new InferenceException("Dimensiones de salida inesperadas");
            }
            return out[0];
        } catch (OrtException e) {
            throw new InferenceException("Falló la inferencia", e);
        }
    }

    public int tokenCount(String text) {
        return tokenizer.encode(text).getIds().length;
    }

    private static void validateInterface(OrtSession s) throws OrtException {
        Map<String, NodeInfo> in = s.getInputInfo();
        for (String name : new String[]{INPUT_IDS, ATTENTION_MASK}) {
            NodeInfo n = in.get(name);
            if (n == null || !(n.getInfo() instanceof TensorInfo t) || t.type != OnnxJavaType.INT64) {
                throw new ModelUnavailableException("Entrada incompatible o ausente: " + name);
            }
        }
        if (in.size() != 2) {
            throw new ModelUnavailableException("El modelo declara entradas no soportadas");
        }
        NodeInfo out = s.getOutputInfo().get(OUTPUT);
        if (out == null || !(out.getInfo() instanceof TensorInfo t) || t.type != OnnxJavaType.FLOAT
                || t.getShape().length != 2 || t.getShape()[1] != ToxicityCategory.values().length) {
            throw new ModelUnavailableException("Salida incompatible o ausente: " + OUTPUT);
        }
    }

    private static void requireReadable(Path p, String what) {
        if (p == null || !Files.isRegularFile(p) || !Files.isReadable(p)) {
            throw new ModelUnavailableException("No se encuentra o no se puede leer el archivo de " + what);
        }
    }

    private static void closeQuietly(OrtSession s) {
        if (s != null) {
            try {
                s.close();
            } catch (OrtException ignored) {
                // nada que hacer durante la limpieza
            }
        }
    }

    @Override
    public void destroy() throws OrtException {
        session.close();
        tokenizer.close();
    }
}
