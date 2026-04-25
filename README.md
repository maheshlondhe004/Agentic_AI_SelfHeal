# Node.js Login CRUD Server

A simple Node.js server for login CRUD operations with hardcoded JSON responses.

## Setup

1. Install dependencies:
```bash
npm install
```

2. Start the server:
```bash
npm start
```

The server will run on `http://localhost:3000`

## API Endpoints

### GET /login
Get all logins
```bash
curl http://localhost:3000/login
```

### GET /login/:id
Get a specific login by ID
```bash
curl http://localhost:3000/login/1
```

### POST /login
Create a new login
```bash
curl -X POST http://localhost:3000/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "email": "newuser@example.com",
    "status": "active"
  }'
```

### PUT /login/:id
Update a login
```bash
curl -X PUT http://localhost:3000/login/1 \
  -H "Content-Type: application/json" \
  -d '{
    "username": "updateduser",
    "email": "updated@example.com",
    "status": "inactive"
  }'
```

### DELETE /login/:id
Delete a login
```bash
curl -X DELETE http://localhost:3000/login/1
```

## Hardcoded Sample Data

The server comes with 3 pre-loaded login records:
- ID 1: user1
- ID 2: user2
- ID 3: user3

All responses follow a consistent JSON structure with `success`, `message`, and `data` fields.
