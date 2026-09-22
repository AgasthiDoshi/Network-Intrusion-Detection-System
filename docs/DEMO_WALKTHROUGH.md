# Five-minute demo walkthrough

## Before presenting

Install JDK 17+ (a JRE alone cannot build), open a terminal in the repository, and run `./run.ps1 test` on Windows or `sh run.sh test` on Linux/macOS. Then launch `./run.ps1` or `sh run.sh` in a graphical desktop session. Startup trains the models locally; no Internet connection is required after cloning and installing Java.

## 0:00 - Explain the objective

“This Java application demonstrates the paper's hybrid intrusion-detection idea using three trained classifiers, signature rules and a normal-traffic anomaly baseline. It analyzes prepared network-flow records and predicts normal or suspicious behavior. This is a synthetic-data classroom demo, not a production network sensor.”

## 0:40 - Show the dashboard

Point out the 400 evaluation flows, the number of alerts, and the 1,200 independent training samples. The right panel shows actual measured metrics and feature importance. Explain why high synthetic-data accuracy cannot establish real-world performance.

## 1:15 - Replay and inspect

1. Click **Replay traffic**. Rows appear in groups of three.
2. Click **Pause replay**, then **Resume replay** to demonstrate progress preservation.
3. Select a flow to display its numeric inputs, model scores and anomaly explanation.
4. Click column headings to sort. Ground-truth attack categories appear in **ACTUAL**; binary predictions appear in **DECISION**.
5. Find a row with **No rule matched** but **ALERT**. Explain how learned models detect patterns beyond the illustrative rules.

## 2:10 - Explore alert tradeoffs

Enable **Alerts only**. Move the threshold toward 0.80: fewer flows should become alerts. Move it toward 0.30: more flows should become alerts. Accuracy, precision, recall and the confusion matrix update for currently replayed labeled rows. The filter affects table visibility only, not metric denominators. Return to 0.50.

## 2:50 - Import and export

Use **Import CSV** and choose `data/demo.csv`. It contains benign web traffic, four attack illustrations, and an unlabeled record. The unknown label is excluded from metrics. Select the SYN flood row and show its matching rule. **Export results** writes every analyzed record and explanation, even when the table is filtered. Existing files require overwrite confirmation.

## 3:40 - Show retraining

Click **Retrain from CSV** and select `data/train.csv`. Training runs in the background. Import `data/test.csv` to return to the 400-flow evaluation. Explain that training and test feature overlap is rejected. Incorrect headers, missing numbers or invalid ratios produce a readable error and retain the previous data/model.

## 4:15 - Reproduce evaluation

Run:

```text
java -jar build/nids-demo.jar evaluate --train data/train.csv --input data/test.csv --output output
```

Open `output/metrics.txt` and `output/predictions.csv`. For the bundled data at threshold 0.50, the ensemble has 186 true positives, 202 true negatives, 4 false positives and 8 false negatives. Compare all seven detection approaches.

## 4:45 - Close with limitations

Explain that this demo implements decision tree, random forest, SVM, rules, anomaly detection and fusion. CNN/RNN, public-dataset benchmarking and live packet capture are future extensions, not hidden implemented capabilities. Cite the IEEE paper and show the student-details section in the README.

## Common questions

**Why Java?** The UI, preprocessing, training, evaluation and export use only Java standard-library APIs, matching the project requirement.

**Does it stop an attack?** No. It flags flow records for analysis; there is no traffic blocking.

**Is the displayed score a probability?** No. It is an uncalibrated model-fusion score.

**Why no CNN/RNN?** The supplied paper does not provide architectures or reproducible training details. Meaningful temporal learning also needs suitable ordered data. This demo implements an explicit, testable subset and documents the gap.

**Can real data be used?** Yes, after converting genuinely available flow/telemetry features into the documented schema. Do not invent missing features or relabel synthetic data as a real benchmark. Use independent sessions/time periods for training and testing.
