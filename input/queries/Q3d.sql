SELECT COUNT(*) FROM orders as o, lineitem as l WHERE o.stream_part = 'R' AND l.stream_part = 'R' AND  l.l_orderkey = o.o_orderkey;
