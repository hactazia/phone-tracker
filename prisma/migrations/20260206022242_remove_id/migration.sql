/*
  Warnings:

  - The primary key for the `locations` table will be changed. If it partially fails, the table could be left without primary key constraint.
  - You are about to drop the column `id` on the `locations` table. All the data in the column will be lost.

*/
-- RedefineTables
PRAGMA defer_foreign_keys=ON;
PRAGMA foreign_keys=OFF;
CREATE TABLE "new_locations" (
    "uid" TEXT NOT NULL,
    "latitude" REAL NOT NULL,
    "longitude" REAL NOT NULL,
    "accuracy" REAL,
    "requested" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "created" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY ("uid", "requested")
);
INSERT INTO "new_locations" ("accuracy", "created", "latitude", "longitude", "requested", "uid") SELECT "accuracy", "created", "latitude", "longitude", "requested", "uid" FROM "locations";
DROP TABLE "locations";
ALTER TABLE "new_locations" RENAME TO "locations";
CREATE INDEX "locations_requested_idx" ON "locations"("requested");
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;
