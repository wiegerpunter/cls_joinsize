SELECT COUNT(*) FROM customer as c, orders as o WHERE c.stream_part = 'R' AND o.stream_part = 'R' AND  c.c_custkey = o.o_custkey;
