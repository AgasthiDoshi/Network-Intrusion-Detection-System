# Network Intrusion Detection System (five-file edition)

This is a compact version of [AgasthiDoshi's Java network intrusion detection demo](https://github.com/AgasthiDoshi/Network-Intrusion-Detection-System). It classifies **prepared network-flow CSV records** as `NORMAL` or `ALERT` using a saved decision tree and shows results in a Java Swing desktop window. It does not capture live packets or block traffic.

## Files

| File | Purpose |
| --- | --- |
| `Backend.java` | CSV validation, model loading, prediction, metrics, and CLI export |
| `Frontend.java` | Desktop table, CSV import, threshold control, and export |
| `model.txt` | Saved 35-node decision tree |
| `requirement.txt` | Runtime and build requirements |
| `README.md` | Setup and CSV format |

The source project trained decision-tree, forest, and SVM models in memory but did not ship a saved model. This edition saves a single decision tree trained on its original `data/train.csv` synthetic flows. It uses the same nine transformed input features. The training CSV is not needed at runtime. The model is a project-specific demonstration artifact; no outside model with an incompatible feature schema was substituted.

## Run

Install a JDK 17 or newer. From this directory:

```sh
mkdir build
javac -d build Backend.java Frontend.java
java -cp build Frontend model.txt
```

In the desktop window, choose **Open CSV**, adjust the alert threshold if needed, and choose **Export predictions** to save a CSV. The default alert threshold is `0.50`.

For command-line analysis:

```sh
java -cp build Backend input.csv model.txt predictions.csv 0.5
```

The output argument and threshold are optional. Run `java -cp build Backend --help` for the argument order. On Windows PowerShell the commands above work with `build` as shown.

## Input CSV

Use this exact header and column order:

```csv
id,protocol,duration_ms,bytes,packets_per_second,syn_ratio,failed_logins,destination_ports,label
normal-web,TCP,600,12000,42,0.1,0,2,NORMAL
syn-flood,TCP,60,20000,2500,0.92,0,2,DOS
port-scan,TCP,120,900,220,0.72,0,75,PORT_SCAN
```

`protocol` must be `TCP`, `UDP`, or `ICMP`. `label` may be `NORMAL`, `DOS`, `PORT_SCAN`, `BRUTE_FORCE`, `EXFILTRATION`, or `UNKNOWN`; an empty label is treated as `UNKNOWN`. Labels are used only for evaluation and are never fed to the model. IDs must be unique and contain letters, digits, `_`, or `-`. Values must be nonnegative finite numbers, and `syn_ratio` must be between 0 and 1. This minimal CSV reader does not support quoted fields.

## Model and limits

The saved tree uses log-transformed numeric fields and one-hot encoded protocols. Each leaf contains an attack score; a score at or above the selected threshold is an alert. The original source's signature rules, random forest, SVM, replay, retraining UI, and detailed model comparison are outside this compact edition.

On the source project's separate synthetic `data/test.csv`, the saved model classified **383/400 flows correctly (95.8%)** at a 0.50 threshold. This is an educational synthetic-data result and does not establish real-world intrusion detection performance. The input must already contain aggregated flow features; raw PCAPs or packets are not accepted.
