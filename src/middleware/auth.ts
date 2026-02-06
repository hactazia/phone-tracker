import { Request, Response, NextFunction } from 'express';

// Middleware d'authentification
export const authMiddleware = (req: Request, res: Response, next: NextFunction) => {
  const authHeader = req.headers.authorization;
  
  // Vérifier si le header Authorization est présent
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({
      success: false,
      error: 'Unauthorized: Missing or invalid token',
    });
  }

  // Extraire le token
  const token = authHeader.substring(7); // Enlever "Bearer "
  
  // Vérifier le token avec la variable d'environnement
  const validToken = process.env.AUTH_TOKEN || 'your_secret_token';
  
  if (token !== validToken) {
    return res.status(403).json({
      success: false,
      error: 'Forbidden: Invalid token',
    });
  }

  // Token valide, continuer
  next();
};
