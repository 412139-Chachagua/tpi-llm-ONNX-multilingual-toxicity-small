"""Exporta bgonzalezbustamante/bert-spanish-toxicity (safetensors) a ONNX fp32.

Se ejecuta dentro de un contenedor (ver scripts/export-spanish-model.sh); no es parte de la aplicación.
Entradas: /work/src (artefactos descargados). Salida: /work/out/model.onnx.
"""
import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

tok = AutoTokenizer.from_pretrained("/work/src")
model = AutoModelForSequenceClassification.from_pretrained("/work/src").eval()
enc = tok("Sos un inútil", return_tensors="pt")
names = ["input_ids", "attention_mask", "token_type_ids"]
torch.onnx.export(
    model, (enc["input_ids"], enc["attention_mask"], enc["token_type_ids"]), "/work/out/model.onnx",
    input_names=names, output_names=["logits"],
    dynamic_axes={**{n: {0: "batch", 1: "seq"} for n in names}, "logits": {0: "batch"}},
    opset_version=17, dynamo=False)
print("id2label:", model.config.id2label)
