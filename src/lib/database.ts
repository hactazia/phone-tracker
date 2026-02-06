import { Location } from "../../generated/prisma/client";
import prisma from "./prisma";

export interface FindMany<T> {
    data: T[];
    total: number;
    limit: number;
    offset: number;
}

export async function locations(uid: string, limit: number = 20, offset: number = 0): Promise<FindMany<Location>> {
    const [data, total] = await prisma.$transaction([
        prisma.location.findMany({
            where: { uid },
            orderBy: { requested: 'desc' },
            take: limit,
            skip: offset,
        }),
        prisma.location.count({ where: { uid } }),
    ]);
    return { data, total, limit, offset };
}