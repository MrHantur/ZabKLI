'''
Используется исключительно для проверки работоспособности
определённых функций, если нужно быстро проверить получаемый
ответ от сервера. Вообще нормально используется похожая
вариация на Kotlin в самом приложении (см. app/src/main/java/ru/zabkli)
'''


import socket

host = "185.177.216.236"
#host = "localhost"
port = 1717
#data = "1$9b$1$1$Разговор о важном$1$"
#data = "3$"
#data = "4$get$"
#data = "5$2$"
#data = "0$"
data = input()
#data = "2$11b"

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
    # Connect to server and send data
    sock.connect((host, port))
    sock.sendall(bytes(data + '\n', "utf-8"))

    # Receive data from the server and shut down
    received = str(sock.recv(10000), "utf-8")

print("Sent:     ", data)
print("Received: ", received)