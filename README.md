# 🏋️ LiftCurve

<p align="center">
  <img src="docs/images/demo.gif" alt="LiftCurve Demo" width="900">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java_21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"/>
  <img src="https://img.shields.io/badge/Quarkus-4695EB?style=for-the-badge&logo=quarkus&logoColor=white" alt="Quarkus"/>
  <img src="https://img.shields.io/badge/React-20232A?style=for-the-badge&logo=react&logoColor=61DAFB" alt="React"/>
  <img src="https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white" alt="TypeScript"/>
  <img src="https://img.shields.io/badge/PostgreSQL-336791?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"/>
  <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker"/>
</p>

A modern full-stack gym tracking platform designed to help users manage workouts, track strength progression, monitor training volume, and analyze long-term performance.

Built with **Java (Quarkus)**, **React**, **TypeScript**, **PostgreSQL**, and **Docker**, LiftCurve showcases modern software engineering practices, including RESTful APIs, JWT authentication, Clean Architecture, automated testing, CI/CD, and cloud deployment.

---

# 🚀 Live Demo

### 🌐 Application

https://liftcurve.vercel.app/login

A pre-populated demo account is available, allowing the application to be explored immediately with realistic workout data.

---

# ✨ Key Features

- JWT authentication and authorization
- Workout session management
- Exercise library
- Custom training routines
- Progressive overload tracking
- One Rep Max (1RM) estimation
- Weekly training volume analytics
- Muscle group statistics
- Plateau detection
- Interactive dashboard
- Responsive interface

---

# 📸 Screenshots

## Dashboard

<p align="center">
  <img src="docs/images/dashboard.png" width="900" alt="Dashboard">
</p>

---

## Workout Sessions

<p align="center">
  <img src="docs/images/sessions.png" width="900" alt="Workout Sessions">
</p>

---

## Exercise Library

<p align="center">
  <img src="docs/images/exercises.png" width="900" alt="Exercise Library">
</p>

---

## Training Routines

<p align="center">
  <img src="docs/images/routines.png" width="900" alt="Training Routines">
</p>

---

# 🛠 Tech Stack

### Backend

- Java 21
- Quarkus
- PostgreSQL
- Flyway
- JWT Authentication
- Maven
- JUnit 5
- REST Assured

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

# 🏗 Architecture

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

The application follows a layered architecture that separates presentation, business logic, and persistence, improving maintainability, scalability, and testability.

---

# 🚀 Running Locally

## Prerequisites

- Java 21
- Node.js 20+
- Docker Desktop

## Clone the repository

```bash
git clone https://github.com/Raphael-Sinelli/liftcurve.git
```

## Start PostgreSQL

```bash
docker compose up -d postgres
```

## Run Backend

```bash
cd backend
./mvnw quarkus:dev
```

## Run Frontend

```bash
cd frontend
npm install
npm run dev
```

---

# 🧪 Running Tests

### Backend

```bash
./mvnw test
```

### Frontend

```bash
npm run test
```

---

# 👤 Demo Account

Email

```text
demo@gymtracker.app
```

Password

```text
DemoGymTracker2026!
```

---

# ⭐ Highlights

- Full-stack application architecture
- RESTful API development
- JWT authentication
- PostgreSQL database modeling
- Dockerized development environment
- Automated testing
- CI/CD pipeline
- Cloud deployment
- Clean Architecture principles

---

# 👨‍💻 Author

**Raphael Oliveira Sinelli Mendonça**

GitHub  
https://github.com/Raphael-Sinelli

LinkedIn  
https://linkedin.com/in/raphael-sinelli-675310321
