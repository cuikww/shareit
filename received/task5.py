from mpi4py import MPI
import numpy as np

comm = MPI.COMM_WORLD
size = comm.Get_size()
rank = comm.Get_rank()

data = None

if rank == 0:
    src = np.arange(100)
    data = np.array_split(src, size)

data = comm.scatter(data, root=0)

if rank != 0:  # Tambahkan kondisi agar rank 0 tidak melakukan operasi modulus dengan 0
    result = [num for num in data if num % rank == 0]
else:
    result = [num for num in data if num != 0]  # Jangan lakukan operasi modulus dengan 0

print("rank %d data : " % (rank), result)