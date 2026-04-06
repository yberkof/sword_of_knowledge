import type { SocketHandlerDeps } from "../context.js";
import type { Socket } from "../context.js";

export function registerDisconnectHandler(socket: Socket, { rooms, io, roomToClient }: SocketHandlerDeps): void {
  socket.on("disconnect", () => {
    console.log("User disconnected:", socket.id);
    for (const [roomId, room] of rooms.entries()) {
      const playerIndex = room.players.findIndex((p: { socketId: string }) => p.socketId === socket.id);
      if (playerIndex !== -1) {
        if (room.phase === "waiting") {
          room.players.splice(playerIndex, 1);
          if (room.players.length === 0) {
            rooms.delete(roomId);
          } else {
            io.to(roomId).emit("room_update", roomToClient(room));
          }
        }
        break;
      }
    }
  });
}
