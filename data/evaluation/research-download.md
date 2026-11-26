# Reproduce OULAD, without redistributing learner records

Official UCI download: https://archive.ics.uci.edu/static/public/349/open+university+learning+analytics+dataset.zip
Source/license: https://archive.ics.uci.edu/dataset/349/open+university+learning+analytics+dataset
Acquired bytes: 46748244
Acquired SHA-256: f2ed1902616c1fe8d2824d872c0b7d2d72be435bf0124d077044fe4be2c6d3e4

Use the Java OuladEvaluation command in data/README.md. It downloads to ignored evaluation/raw/ and reads only fixed CSV entries; no extraction/scripts or MySQL import. The 1000-ID CCC/2014B sample, day 40 cutoff, day 80 horizon, train-known candidate universe and eligibility are fixed in source and oulad-manifest.json. No random seed is needed because sampling/ties are deterministic. Output is aggregate only; never export student identifiers, demographics or trajectories. Cite Kuzilek, Hlosta & Zdrahal (2015), DOI 10.24432/C5KK69, CC BY 4.0.
