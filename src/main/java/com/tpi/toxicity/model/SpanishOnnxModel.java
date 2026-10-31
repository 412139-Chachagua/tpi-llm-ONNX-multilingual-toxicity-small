package com.tpi.toxicity.model;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Sesión ONNX, tokenizer y etiquetas de {@code bgonzalezbustamante/bert-spanish-toxicity}, cargados una
 * sola vez al construir el bean. Independiente de {@link OnnxToxicityModel}: tokenizer, entradas y salida
 * propios (BERT: input_ids, attention_mask y token_type_ids; 2 logits). Si algo falla la aplicación no arranca.
 */
@Component
public class SpanishOnnxModel implements DisposableBean {

    static final String INPUT_IDS = "input_ids";
    static final String ATTENTION_MASK = "attention_mask";
    static final String TOKEN_TYPE_IDS = "token_type_ids";
    static final String OUTPUT = "logits";

    private final OrtEnvironment env;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final List<String> labels;
    private volatile boolean ready;

    @Autowired
    public SpanishOnnxModel(SpanishToxicityProperties props) {
        this(props.modelPath(), props.tokenizerPath(), props.configPath(), props.maxTokens(), props.intraOpThreads());
    }

    public SpanishOnnxModel(Path modelPath, Path tokenizerPath, Path configPath, int maxTokens) {
        this(modelPath, tokenizerPath, configPath, maxTokens, 0);
    }

    public SpanishOnnxModel(Path modelPath, Path tokenizerPath, Path configPath, int maxTokens, int intraOpThreads) {
        requireReadable(modelPath, "modelo ONNX español");
        requireReadable(tokenizerPath, "tokenizer español");
        requireReadable(configPath, "configuración del modelo español");
        this.labels = SpanishLabels.load(configPath);
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
            // Por defecto ONNX Runtime usa los núcleos de la máquina; en un contenedor con cuota de vCPU eso genera
            // más hilos que CPU disponibles. Runtime.availableProcessors() respeta el límite del contenedor.
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setIntraOpNumThreads(intraOpThreads > 0 ? intraOpThreads : Runtime.getRuntime().availableProcessors());
            sess = env.createSession(modelPath.toString(), opts);
            validateInterface(sess, labels.size());
        } catch (IOException | OrtException | RuntimeException e) {
            closeQuietly(sess);
            if (tok != null) {
                tok.close();
            }
            throw new ModelUnavailableException("No se pudo cargar el modelo español o su tokenizer", e);
        }
        this.session = sess;
        this.tokenizer = tok;
        try {
            logits("Hola, esto es una prueba de arranque.");
        } catch (RuntimeException e) {
            destroyQuietly();
            throw new ModelUnavailableException("El modelo español no superó la inferencia de arranque", e);
        }
        this.ready = true;
    }

    /** Etiquetas en el orden de los logits de salida. */
    public List<String> labels() {
        return labels;
    }

    /** Devuelve los logits crudos (uno por etiqueta, en el orden de {@link #labels()}). */
    public float[] logits(String text) {
        Encoding enc = tokenizer.encode(text);
        try (OnnxTensor idsT = OnnxTensor.createTensor(env, new long[][]{enc.getIds()});
             OnnxTensor maskT = OnnxTensor.createTensor(env, new long[][]{enc.getAttentionMask()});
             OnnxTensor typeT = OnnxTensor.createTensor(env, new long[][]{enc.getTypeIds()});
             OrtSession.Result result = session.run(
                     Map.of(INPUT_IDS, idsT, ATTENTION_MASK, maskT, TOKEN_TYPE_IDS, typeT))) {
            float[][] out = (float[][]) result.get(OUTPUT).orElseThrow(
                    () -> new InferenceException("El modelo español no devolvió la salida esperada")).getValue();
            if (out.length != 1 || out[0].length != labels.size()) {
                throw new InferenceException("Dimensiones de salida inesperadas");
            }
            return out[0];
        } catch (OrtException e) {
            throw new InferenceException("Falló la inferencia del modelo español", e);
        }
    }

    public boolean isReady() {
        return ready;
    }

    private static void validateInterface(OrtSession s, int numLabels) throws OrtException {
        Map<String, NodeInfo> in = s.getInputInfo();
        for (String name : new String[]{INPUT_IDS, ATTENTION_MASK, TOKEN_TYPE_IDS}) {
            NodeInfo n = in.get(name);
            if (n == null || !(n.getInfo() instanceof TensorInfo t) || t.type != OnnxJavaType.INT64) {
                throw new ModelUnavailableException("Entrada incompatible o ausente: " + name);
            }
        }
        if (in.size() != 3) {
            throw new ModelUnavailableException("El modelo español declara entradas no soportadas");
        }
        NodeInfo out = s.getOutputInfo().get(OUTPUT);
        if (out == null || !(out.getInfo() instanceof TensorInfo t) || t.type != OnnxJavaType.FLOAT
                || t.getShape().length != 2 || t.getShape()[1] != numLabels) {
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

    private void destroyQuietly() {
        closeQuietly(session);
        tokenizer.close();
    }

    @Override
    public void destroy() throws OrtException {
        ready = false;
        session.close();
        tokenizer.close();
    }
}
