<!--
Copyright (c) 2024 RapidStream Design Automation, Inc. and contributors.  All rights reserved.
The contributor(s) of this file has/have agreed to the RapidStream Contributor License Agreement.
-->

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

The RapidStream DSE Engine is an open source project managed by RapidStream Design Automation, Inc., who is authorized by the contributors to license the software under a dual licensing model:

1. **Open-Source License (AGPL):** The RapidStream DSE Engine is available as free and open-source software under the GNU Affero General Public License (AGPL) version 3.0 or later. You can redistribute it and/or modify it under the terms of the AGPL. If you use this software to provide a network service, you must make the complete source code available to users under the AGPL.

2. **Commercial License:** For RapidStream customers who prefer a closed-source, commercial license without the AGPL's requirements, RapidStream Design Automation, Inc. offers a separate commercial license. Please contact info@rapidstream-da.com for more information about obtaining a commercial license.


Contributor License Agreement (CLA)
-----------------------------------

By contributing to this open-source repository, you agree to the RapidStream Contributor License Agreement.

Under this agreement, you grant to RapidStream Design Automation, Inc. and to recipients of software distributed by RapidStream a perpetual, worldwide, non-exclusive, no-charge, royalty-free, irrevocable copyright license to reproduce, prepare derivative works of, publicly display, publicly perform, sublicense, and distribute your contributions and such derivative works. You also grant to RapidStream Design Automation, Inc. and to recipients of software distributed by RapidStream a perpetual, worldwide, non-exclusive, no-charge, royalty-free, irrevocable patent license to make, have made, use, offer to sell, sell, import, and otherwise transfer the work,

Please note that this is a summary of the licensing terms, and the full text of the [AGPL](https://www.gnu.org/licenses/agpl-3.0.txt) and the [RapidStream Contributor License Agreement](CLA.md) should be consulted for detailed legal information.


-----

Copyright (c) 2024 RapidStream Design Automation, Inc. and contributors.  All rights reserved.
