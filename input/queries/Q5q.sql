SELECT COUNT(*) FROM nation as n, region as r WHERE n.stream_part = 'R' AND r.stream_part = 'R' AND  n.n_regionkey = r.r_regionkey;
