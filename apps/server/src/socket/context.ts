import type { Server } from "socket.io";
import type { Socket } from "socket.io";

/** Minimal deps passed into split socket handler modules (avoid cyclic imports with game/attachGameServer). */
export type SocketHandlerDeps = {
  rooms: Map<string, any>;
  io: Server;
  roomToClient: (room: unknown) => unknown;
};

export type { Socket };
