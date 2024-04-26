"""crossing_pblocks_node_counter.py: Extract node # between pblocks."""

__copyright__ = """
Copyright (c) 2024 RapidStream Design Automation, Inc. and contributors.
All rights reserved. The contributor(s) of this file has/have agreed to the
RapidStream Contributor License Agreement.
"""

import argparse
from pathlib import Path
from typing import Literal

import jpype
import jpype.imports

jpype.startJVM(jpype.getDefaultJVMPath(), "-ea")  # enabling assertions
from com.xilinx.rapidwright.examples import (  # noqa: E402,E501 #pylint: disable=E0401,C0413
    CrossingPBlockNodeCounter,
)


def get_node_counts_between_pblocks(
    dcp_path: Path,
    num_col: int,
    num_row: int,
) -> dict[int, dict[int, dict[Literal["N", "S", "E", "W"], int]]]:
    """Extract number of nodes between pblocks.

    Args:
        dcp_path: Path to a checkpoint file which contains a grid of pblocks.
            It is guaranteed that the pblock will be named in the format of
            f"x{x}y{y}".
        num_col: Number of columns of the pblock grid.
        num_row: Number of rows of the pblock grid.

    Returns:
        A mapping from (col, row) to direction to the number of nodes. For
        example, node_count[0][0]["NORTH"] is the number of nodes between
        pblock (0, 0) and (0, 1).

        node_count[0][0] should not have "SOUTH" or "WEST" keys because those
        slots do not exist.
    """
    node_count_java = CrossingPBlockNodeCounter.getAllPBlockCrossingNodeCount(
        jpype.JString(str(dcp_path)), num_col, num_row
    )  # <java object 'java.util.HashMap'>
    node_count = {
        _: {
            __: dict(dict(node_count_java[_])[__])
            for __ in dict(node_count_java[_]).keys()
        }
        for _ in node_count_java.keys()
    }  # dict
    return node_count


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("dcp_path", type=str, help="Path to checkpoint")
    parser.add_argument("num_col", type=int, help="Column# of the pblock grid")
    parser.add_argument("num_row", type=int, help="Row# of the pblock grid")
    args = parser.parse_args()

    dcp_path_input = Path(args.dcp_path)
    num_col_input = args.num_col
    num_row_input = args.num_row

    node_counts = get_node_counts_between_pblocks(
        dcp_path_input, num_col_input, num_row_input
    )
    print(node_counts)
