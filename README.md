# Network Intrusion Detection System

A small Java desktop and command-line application that classifies **prepared network-flow records** as `NORMAL` or `ALERT`. It loads a saved decision tree, reads a CSV of flow measurements, calculates a score for each row, and compares that score with an alert threshold. The desktop window starts with four synthetic examples so the controls and results are visible immediately.

This is an **offline educational demonstration**. It does not capture packets, monitor your network in real time, block connections, identify a specific attack type, or establish that a computer is secure.

## What the screenshot means

The four rows shown when the window opens are hand-picked **synthetic examples**, not current network traffic:

| ID | Ground truth | Model decision at threshold 0.50 | Meaning |
| --- | --- | --- | --- |
| `normal-web` | `NORMAL` | `NORMAL` | Example of a benign flow |
| `syn-flood` | `DOS` | `ALERT` | Example with flood-like measurements |
| `port-scan` | `PORT_SCAN` | `ALERT` | Example with many destination ports |
| `login-failures` | `BRUTE_FORCE` | `ALERT` | Example with many failed logins |

**Ground truth** is the label supplied with the example or CSV; it is *not* a model prediction. **Decision** is the model's binary result. **Score** is a number from 0 to 1 produced by a leaf of the decision tree. At threshold `0.50`, a score of at least `0.50` becomes `ALERT`. The model does not predict `DOS`, `PORT_SCAN`, or `BRUTE_FORCE`; those names only appear as supplied ground-truth labels.

The footer's `100%` accuracy, precision, recall, and F1 apply **only to those four selected examples**. They are useful for checking the interface, not for judging detection quality. Open a separate labeled CSV to evaluate other flows. With unlabeled flows, predictions still work but evaluation metrics are unavailable.

## Files and responsibilities

This edition intentionally has five tracked project files:

| File | Role |
| --- | --- |
| `Backend.java` | Validates CSV, transforms features, loads the tree, scores flows, computes metrics, exports CSV, and offers a CLI |
| `Frontend.java` | Java Swing window, four built-in examples, CSV chooser, table, threshold slider, alert filter, and export button |
| `model.txt` | Saved 35-node decision tree read by `Backend.java` |
| `requirement.txt` | Pip-compatible comments explaining that there are no Python packages and that a JDK is needed |
| `README.md` | This guide |

The `build/` directory is generated locally when you compile; it is not a project source file. The app has no Python service, Maven, Gradle, database, API key, or third-party Java dependency.

## Requirements and run instructions

Install a **JDK 17 or newer**. A JRE alone cannot compile the program. In a new PowerShell window, verify that both commands work:

```powershell
java -version
javac -version
```

If PowerShell says either command is not recognized, install a JDK and add its `bin` directory to your `PATH`, then reopen PowerShell. `pip install -r requirement.txt` succeeds but installs nothing: pip manages Python packages and cannot install the JDK for this Java program.

From the repository directory, build and open the desktop app:

```powershell
New-Item -ItemType Directory -Force build | Out-Null
javac -encoding UTF-8 -d build Backend.java Frontend.java
java -cp build Frontend model.txt
```

Run these commands from the repository root so `model.txt` is found. On Linux or macOS, replace the first command with `mkdir -p build`; the `javac` and `java` commands are the same.

The desktop app starts with the four example rows. To analyze another file, click **Open CSV** and choose it. The **Alert threshold** slider ranges from `0.10` to `0.90`; lowering it can increase alerts and raising it can reduce alerts. **Alerts only** hides normal rows in the table but does not remove them from metrics or export. **Export predictions** writes all loaded rows using the current threshold, even when the table is filtered.

## Run without the desktop window

The CLI is useful for a prepared CSV or for a machine without a graphical desktop:

```powershell
java -cp build Backend input.csv
java -cp build Backend input.csv model.txt predictions.csv 0.5
java -cp build Backend --help
```

The first argument is required. The model path defaults to `model.txt`; the threshold defaults to `0.5`. To set a threshold on the CLI, also provide the model path and output CSV path in the shown order. The output directory must already exist. The CLI prints each decision and a metrics summary, then optionally writes `predictions.csv`.

The exported CSV has these columns:

```csv
id,protocol,ground_truth,prediction,score
```

`prediction` is `NORMAL` or `ALERT`. Exported `score` has six decimal places. Ground-truth labels are copied from the input; the model never creates an attack-category label.

## Input CSV format

The header and column order must match exactly:

```csv
id,protocol,duration_ms,bytes,packets_per_second,syn_ratio,failed_logins,destination_ports,label
normal-web,TCP,600,12000,42,0.1,0,2,NORMAL
syn-flood,TCP,60,20000,2500,0.92,0,2,DOS
port-scan,TCP,120,900,220,0.72,0,75,PORT_SCAN
login-failures,TCP,1500,8000,30,0.15,24,1,BRUTE_FORCE
```

Save this text as a UTF-8 `.csv` file, then open it in the app. Each row describes an **already aggregated flow**, not a raw packet. The source of your data must calculate the measurements before this application can use them.

| Column | Meaning and accepted value |
| --- | --- |
| `id` | Unique flow identifier, 1-64 letters, digits, `_`, or `-` |
| `protocol` | Exactly `TCP`, `UDP`, or `ICMP` |
| `duration_ms` | Duration in milliseconds, nonnegative number |
| `bytes` | Transferred bytes, nonnegative number |
| `packets_per_second` | Observed packet rate, nonnegative number |
| `syn_ratio` | SYN proportion from `0` through `1` |
| `failed_logins` | Failed login count, nonnegative number |
| `destination_ports` | Destination port count, nonnegative number |
| `label` | `NORMAL`, `DOS`, `PORT_SCAN`, `BRUTE_FORCE`, `EXFILTRATION`, or `UNKNOWN`; blank becomes `UNKNOWN` |

All numeric values must be finite and at most `1e12`. The file limit is 10 MB and 10,000 nonblank flows. Duplicate IDs, an incorrect header, unsupported categories, and malformed rows are rejected with a CSV line number. This simple parser does not support quoted CSV fields. For your own unlabeled flows, leave `label` empty or use `UNKNOWN`.

## How a decision is made

```text
Built-in examples or input CSV
       -> validate each flow
       -> transform numeric fields and encode protocol
       -> follow the saved decision-tree branches
       -> read the leaf's attack score
       -> compare score with threshold
       -> display or export NORMAL / ALERT

Supplied ground-truth label -----------------> evaluation metrics only
```

The nine model inputs, in order, are `log1p(duration_ms)`, `log1p(bytes)`, `log1p(packets_per_second)`, `syn_ratio`, `log1p(failed_logins)`, `log1p(destination_ports)`, and three one-hot indicators for `TCP`, `UDP`, and `ICMP`. `log1p(x)` means the natural logarithm of `1 + x`. The ID and ground-truth label are never used to calculate the score.

`model.txt` is a **trained model artifact**, not the training dataset. Its first line, `NIDS_TREE_V1`, identifies the format. Each later node lists `feature_index,threshold,left_node,right_node,attack_score`. A non-leaf node tests one feature and chooses a child; a leaf (`feature_index = -1`) supplies the final score. During training, splits were selected by Gini impurity with a maximum depth of seven and at least eight training rows on either side of a split. Each leaf's score is the fraction of attack-labeled training rows that reached it. Text storage lets plain Java load the model without a machine-learning library. Scores are **not calibrated probabilities**; a displayed `0.800` should not be read as an 80% real-world chance of an attack.

The source project trained models in memory but did not include a saved model file. This edition trained one decision tree from the original project's **1,200 synthetic training flows** and saved it as `model.txt`. It is loaded for inference at startup; the five-file app does not retrain it. For the original implementation and synthetic data, see the [pre-reduction source commit](https://github.com/AgasthiDoshi/Network-Intrusion-Detection-System/tree/39c3c1f).

## How evaluation works

Only rows whose `label` is known contribute to the metrics. `NORMAL` is the negative class; all other known labels are grouped into the positive **attack** class. The threshold turns each score into a binary decision:

| Term | Meaning |
| --- | --- |
| TP | Attack-labeled flow predicted `ALERT` |
| FP | Normal-labeled flow predicted `ALERT` |
| TN | Normal-labeled flow predicted `NORMAL` |
| FN | Attack-labeled flow predicted `NORMAL` |

Accuracy is `(TP + TN) / (TP + FP + TN + FN)`. Precision is `TP / (TP + FP)`. Recall is `TP / (TP + FN)`. F1 is the harmonic mean of precision and recall. When a precision or recall denominator is zero, this demo displays `0` for that measure. If there are no labeled rows, it displays that metrics are unavailable.

On the original project's **separate 400-row synthetic test set** at threshold `0.50`, this saved tree produced TP `185`, FP `8`, TN `198`, and FN `9`: **383/400 correct (95.75% accuracy)**. Precision was `95.85%`, recall `95.36%`, and F1 `95.61%`. This checks behavior on a separate synthetic set. It is **not** evidence of performance on real network traffic or on other datasets.

## Scope and limits

- The program reads prepared flow CSV files. It has no live packet capture, PCAP reader, traffic replay, firewall action, or background network monitoring.
- It predicts only `NORMAL` versus `ALERT`. The supplied attack labels are for evaluation and display, not multiclass predictions.
- The single saved tree replaces the original demo's random forest, SVM, anomaly baseline, and signature-rule ensemble. Those original features are not present in this five-file edition.
- The model was learned from synthetic data. Different networks, feature definitions, class frequencies, and attack patterns may give very different results. Do not use it as a production security control.
- The four startup examples were chosen to demonstrate the interface. Their `100%` metric is not a validation result.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| `java` or `javac` is not recognized | Install JDK 17+, put its `bin` directory on `PATH`, and open a new PowerShell window |
| `pip install -r requirement.txt` installs nothing | Expected: there are no Python dependencies; pip cannot install Java |
| `Could not find or load main class Frontend` | Compile both `.java` files with `javac -encoding UTF-8 -d build Backend.java Frontend.java`, then use `java -cp build Frontend model.txt` |
| `model.txt` not found | Run from the repository root, or supply an absolute model path |
| CSV rejected | Compare the exact header and allowed values above; the error includes the row number |
| Metrics show `100%` on launch | Those are four curated examples; load a separate labeled CSV to evaluate more data |
| Table shows fewer rows than the loaded file | Turn off **Alerts only**; the filter affects visibility, not evaluation or export |
