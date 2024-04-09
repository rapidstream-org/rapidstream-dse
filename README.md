<!--
Copyright (c) 2024 RapidStream Design Automation, Inc. and contributors.
All rights reserved. The contributor(s) of this file has/have agreed to the
RapidStream Contributor License Agreement.
-->

<img src="https://imagedelivery.net/AU8IzMTGgpVmEBfwPILIgw/1b565657-df33-41f9-f29e-0d539743e700/128" width="64px" alt="RapidStream Logo" />


RapidStream DSE Engine
======================

Purpose
-------

This project analyzes the placement and routing results from the EDA tools, and uses them as feedback to adjust the partition-and-pipeline parameters of the RapidStream software.

Requirements
------------

- `Python3`: 3.10 or later.
- `poetry`: to manage virtual environment and dependencies.

Installation
------------

```bash
curl -fsSL https://install.python-poetry.org/ | python3.10 -
poetry install --with=dev
```

Before Committing
-----------------

All tests and pre-commit checks shall pass before committing to the main repository.

Before your first commit, you must install pre-commit for Git:

```bash
poetry run pre-commit install
```

To invoke `pre-commit` without committing the changes:

```bash
poetry run pre-commit
```

License
-------

The RapidStream DSE Engine is an open source project under [Apache License, Version 2.0](LICENSE). By contributing to this open-source repository, you agree to the [RapidStream Contributor License Agreement](CLA.md).

-----

Copyright (c) 2024 RapidStream Design Automation, Inc. and contributors.  All rights reserved.
