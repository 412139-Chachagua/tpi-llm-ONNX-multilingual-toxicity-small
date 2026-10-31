"""Resume una corrida de docs/postman/rioplatense-eval.postman_collection.json.

Uso:
  postman collection run docs/postman/rioplatense-eval.postman_collection.json \
    -d docs/evaluation/rioplatense-pilot.csv --env-var baseUrl=... --env-var apiKey=... > run.log
  python3 scripts/eval-rioplatense.py docs/evaluation/rioplatense-pilot.csv run.log

La colección registra por cada texto una línea E|id|s|max_categorías|toxicity (modelo small) y E|id|e|TOXIC (modelo español).
Imprime aciertos (TP) y falsos positivos (FP) por umbral para cada modelo, OR y AND, y el detalle por texto.
"""
import csv
import re
import sys


def load(data_path, log_path):
    rows = {r["id"]: r for r in csv.DictReader(open(data_path, encoding="utf-8"))}
    res = {}
    for line in open(log_path, encoding="utf-8"):
        m = re.search(r"E\|(\d+)\|([se])\|([\d.]+)", line)
        if m:
            res.setdefault(m.group(1), {})[m.group(2)] = float(m.group(3))
    missing = [i for i in rows if set(res.get(i, {})) != {"s", "e"}]
    if missing:
        sys.exit(f"Faltan resultados para los ids: {missing}")
    return [(int(r["label"]), r["grupo"], r["text"], res[i]["s"], res[i]["e"]) for i, r in rows.items()]


def count(data, pred):
    tp = sum(1 for d, p in zip(data, pred) if d[0] == 1 and p)
    fp = sum(1 for d, p in zip(data, pred) if d[0] == 0 and p)
    return tp, fp


def main():
    data = load(sys.argv[1], sys.argv[2])
    pos = sum(d[0] for d in data)
    print(f"{len(data)} textos: {pos} tóxicos, {len(data) - pos} no tóxicos")
    print("umbral | small TP FP | es TP FP | OR TP FP | AND TP FP")
    for u in [0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9]:
        a = count(data, [d[3] >= u for d in data])
        b = count(data, [d[4] >= u for d in data])
        o = count(data, [d[3] >= u or d[4] >= u for d in data])
        n = count(data, [d[3] >= u and d[4] >= u for d in data])
        print(f"{u:.1f}    | {a[0]:3d} {a[1]:3d}    | {b[0]:3d} {b[1]:3d} | {o[0]:3d} {o[1]:3d} | {n[0]:3d} {n[1]:3d}")
    print("\nlabel grupo | small | es | texto")
    for d in data:
        print(f"{d[0]} {d[1]:<18}| {d[3]:.2f} | {d[4]:.2f} | {d[2]}")


if __name__ == "__main__":
    main()
