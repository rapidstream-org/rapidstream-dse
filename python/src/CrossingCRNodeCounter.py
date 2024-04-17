from pathlib import Path
from typing import Literal
import jpype
import jpype.imports
import sys

def get_node_counts_between_CRs(
    dcp_path: Path,
    num_col: int,
    num_row: int,
) -> dict[Literal["N", "S", "E", "W"], int]:
    """Extract number of nodes between a **single** clock region and its adjacent 
       ones (at clock region level).

    Args:
        dcp_path: Path to a checkpoint file which contains a grid of CRs.
        num_col: Number of columns (X) of the pblock grid.
        num_row: Number of rows (Y) of the pblock grid.

    Returns:
        A dictionrary direction to the number of nodes. For example, for Alveo 
        U280, num_col: 0, num_row: 0, the output is {'E': 12480, 'N': 9200}.

        CR_X0Y0 should not have "SOUTH" or "WEST" keys because those slots
        do not exist.
    """
    jpype.startJVM(jpype.getDefaultJVMPath())
    from com.xilinx.rapidwright.examples import CrossingCRNodeCounter
    node_count_java = CrossingCRNodeCounter.getSinglePBlockCrossingCRNodeCount(jpype.JString(str(dcp_path)), num_col, num_row) # <java object 'java.util.HashMap'>
    node_count = dict(node_count_java) # dict
    '''
    # enabling assertions by "-ea"
    process = jpype.java.lang.Runtime.getRuntime().exec_(
        "java -ea com.xilinx.rapidwright.examples.CrossingCRNodeCounter {} {} {}".format(dcp_path, num_col, num_row))
    process.waitFor()

    # get stdout
    input_stream = process.getInputStream()
    reader = jpype.java.io.BufferedReader(jpype.java.io.InputStreamReader(input_stream))

    # read commandline output
    line = reader.readLine()
    while line is not None:
        print(line)
        line = reader.readLine()
    '''
    return node_count

def get_node_counts_between_CRs(
    dcp_path: Path
) -> dict[int, dict[int, dict[Literal["N", "S", "E", "W"], int]]]:
    """Extract number of nodes between **all pairs of** pblocks (at clock region 
       level).

    Args:
        dcp_path: Path to a checkpoint file which contains a grid of pblocks. It is
            guaranteed that the pblock will be named in the format of f"x{x}y{y}".

    Returns:
        A mapping from (col, row) to direction to the number of nodes. For example,
        node_count[0][0]["N"] is the number of nodes between pblock (0, 0) and
        (0, 1).

        node_count[0][0] should not have "SOUTH" or "WEST" keys because those slots
        do not exist.
    """
    jpype.startJVM(jpype.getDefaultJVMPath(), "-ea")    # enabling assertions by "-ea"
    from com.xilinx.rapidwright.examples import CrossingCRNodeCounter
    node_count_java = CrossingCRNodeCounter.getAllPBlockCrossingCRNodeCount(jpype.JString(str(dcp_path))) # <java object 'java.util.HashMap'>
    node_count = {_:{__:dict(dict(node_count_java[_])[__]) for __ in dict(node_count_java[_]).keys()} for _ in node_count_java.keys()} # dict
    return node_count
    
if __name__ == "__main__":
    if len(sys.argv) not in {2, 4}:
        print("Usage: python CrossingCRNodeExtractor.py <dcp_path> <num_col> <num_row>\n       or python CrossingCRNodeExtractor.py <dcp_path>")
        sys.exit(1)
    
    try:
        dcp_path = Path(sys.argv[1])
        num_col = int(sys.argv[2]) if len(sys.argv) == 4 else -1
        num_row = int(sys.argv[3]) if len(sys.argv) == 4 else -1
    except Exception as e:
        print("Error: Invalid arguments.")
        sys.exit(1)
        
    # node_counts = get_node_counts_between_CRs(dcp_path, num_col, num_row)
    node_counts = get_node_counts_between_CRs(dcp_path)
    print(node_counts)