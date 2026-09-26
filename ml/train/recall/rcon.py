import socket
import struct

TYPE_RESPONSE = 0
TYPE_COMMAND = 2
TYPE_AUTH_RESPONSE = 2
TYPE_LOGIN = 3
AUTH_FAILED = -1
HEADER = struct.Struct("<iii")
MAX_BODY = 1446


class RconError(Exception):
    pass


def encode_packet(request_id, kind, body):
    payload = body.encode("utf-8")
    if len(payload) > MAX_BODY:
        raise RconError(f"command longer than {MAX_BODY} bytes")
    return HEADER.pack(len(payload) + 10, request_id, kind) + payload + b"\x00\x00"


def decode_packet(data):
    if len(data) < HEADER.size + 2:
        raise RconError("truncated packet")
    length, request_id, kind = HEADER.unpack_from(data)
    if length + 4 != len(data) or data[-2:] != b"\x00\x00":
        raise RconError("malformed packet")
    return request_id, kind, data[HEADER.size:-2].decode("utf-8", errors="replace")


class Rcon:
    def __init__(self, host, port, password, timeout=30.0):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.next_id = 0
        request_id = self._send(TYPE_LOGIN, password)
        response_id, kind, _ = self._receive()
        while kind != TYPE_AUTH_RESPONSE:
            response_id, kind, _ = self._receive()
        if response_id == AUTH_FAILED or response_id != request_id:
            self.close()
            raise RconError("rcon authentication failed")

    def _send(self, kind, body):
        self.next_id += 1
        self.sock.sendall(encode_packet(self.next_id, kind, body))
        return self.next_id

    def _read(self, count):
        chunks = bytearray()
        while len(chunks) < count:
            chunk = self.sock.recv(count - len(chunks))
            if not chunk:
                raise RconError("connection closed by server")
            chunks += chunk
        return bytes(chunks)

    def _receive(self):
        head = self._read(4)
        (length,) = struct.unpack("<i", head)
        return decode_packet(head + self._read(length))

    def command(self, text):
        request_id = self._send(TYPE_COMMAND, text)
        while True:
            response_id, kind, body = self._receive()
            if response_id == request_id and kind == TYPE_RESPONSE:
                return body

    def close(self):
        self.sock.close()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()
