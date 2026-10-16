SELECT COUNT(*) FROM lineitem as l, supplier as s WHERE l.stream_part = 'R' AND s.stream_part = 'R' AND  l.l_suppkey = s.s_suppkey;
