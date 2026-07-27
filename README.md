# 🏋️ LiftCurve

<p align="center">
  <img src="docs/images/demo.gif" alt="LiftCurve Demo" width="100%">
</p>

<p align="center">

[![CI](https://github.com/Raphael-Sinelli/liftcurve/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/Raphael-Sinelli/liftcurve/actions/workflows/ci.yml?query=branch%3Adevelop)

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Quarkus](https://img.shields.io/badge/Quarkus-3-blue?logo=quarkus&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-336791?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white)

</p>

A full-stack workout tracking platform built with Java, Quarkus, React and TypeScript.

LiftCurve helps users manage exercises, create workout routines, record workout sessions and monitor long-term strength progression through interactive analytics such as One Rep Max (1RM), weekly training volume and plateau detection.

## 🚀 Live Demo

**Application:** https://liftcurve.vercel.app

Use **"Entrar como Visitante (Conta Demo)"** on the login screen to explore the application without creating an account.

---

## ✨ Features

- JWT authentication
- Exercise management
- Workout routine management
- Workout session tracking
- Interactive dashboard
- One Rep Max (1RM) estimation
- Weekly training volume analytics
- Muscle group volume aggregation
- Automatic plateau detection
- Responsive interface

---

## 📸 Screenshots

### Login
![](docs/images/login.png)

### Dashboard
![](docs/images/dashboard.png)

### Workout Sessions
![](docs/images/sessions.png)

### Exercise Library
![](docs/images/exercises.png)

### Training Routines
![](docs/images/routines.png)

---

## 🛠 Tech Stack

### Backend
- Java 21
- Quarkus
- Hibernate Panache
- PostgreSQL
- Flyway
- JWT Authentication
- Maven

### Frontend
- React
- TypeScript
- Vite
- Tailwind CSS
- React Router
- Axios
- Recharts

### DevOps
- Docker
- Docker Compose
- GitHub Actions
- Render
- Vercel

---

## 🏗 Architecture

```text
React + TypeScript
        │
        ▼
REST API (Quarkus)
        │
        ▼
Business Layer
        │
        ▼
PostgreSQL
```

---

## 🚀 Running Locally

```bash
git clone https://github.com/Raphael-Sinelli/liftcurve.git
cd liftcurve

docker compose up -d
```

### Backend

```bash
cd backend
./mvnw quarkus:dev
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

---

## 🧪 Running Tests

### Backend

```bash
./mvnw test
```

### Frontend

```bash
npm test
```

---

## 🚀 Deployment

- **Frontend:** Vercel
- **Backend:** Render
- **Database:** PostgreSQL

---

## 📚 Documentation

Additional project documentation is available in the `docs/` directory.

---

## 👨‍💻 Author

**Raphael Oliveira Sinelli Mendonça**

- GitHub: https://github.com/Raphael-Sinelli
- LinkedIn: https://www.linkedin.com/in/raphael-sinelli-675310321/
