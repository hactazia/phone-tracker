# Utiliser l'image officielle Node.js
FROM node:20-alpine

# Définir le répertoire de travail
WORKDIR /app

# Copier les fichiers de dépendances
COPY package*.json ./
COPY tsconfig.json ./
COPY prisma.config.ts ./

# Installer les dépendances
RUN npm ci --only=production && \
    npm install -g tsx

# Copier le schéma Prisma et générer le client
COPY prisma ./prisma
RUN npx prisma generate

# Copier le code source
COPY src ./src
COPY public ./public

# Créer le dossier pour la base de données
RUN mkdir -p /app/prisma/data

# Exposer le port
EXPOSE 3000

# Définir les variables d'environnement par défaut
ENV NODE_ENV=production
ENV PORT=3000
ENV DATABASE_URL="file:/app/prisma/data/dev.db"

# Commande pour démarrer l'application
CMD npx prisma migrate deploy && npm start
