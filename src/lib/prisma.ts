import 'dotenv/config';
import { PrismaBetterSqlite3 } from "@prisma/adapter-better-sqlite3";
import { PrismaClient } from '../../generated/prisma/client';

const prisma = new PrismaClient({
    adapter: new PrismaBetterSqlite3({
        url: process.env.DATABASE_URL ?? '',
    })
});

export { prisma };
export default prisma;