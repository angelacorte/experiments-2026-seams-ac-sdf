"""Check of the shares and of Jain's index of plot_positions_fairness.py, and of the anchors it draws:
python python/test_fairness.py"""

import numpy as np
import pandas as pd

from plot_positions_fairness import jain, nearest, shares
from positions import triangulating

strip = np.column_stack([np.linspace(0.05, 1.95, 20), np.full(20, 0.5)])  # Points of a 2 x 1 rectangle
owners = nearest(strip, np.array([[0.5, 0.5], [1.5, 0.5], [9.0, 9.0]]))  # Two devices in it, one far away
assert list(owners) == [0] * 10 + [1] * 10
assert list(shares(owners, 3)) == [1.5, 1.5, 0.0]
assert jain(np.ones(4)) == 1.0 and jain(np.array([4.0, 0.0, 0.0, 0.0])) == 0.25
# A lone leader far away (id 0) and the anchors that fix the frame (ids 1-3): only the latter are drawn
lone = pd.DataFrame({"time": 0.0, "id": [0, 1, 2, 3], "x": [50.0, 0, 5, 2], "y": [50.0, 0, 0, 4], "sdf": 0.0,
                     "leader": 1, "anchor": [1, 1, 2, 3]})
kept = triangulating(lone)
assert list(kept["anchor"]) == [0, 1, 2, 3] and list(kept["leader"]) == [0, 1, 1, 1]
print("ok")
