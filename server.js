const express = require('express');
const app = express();
const PORT = 3000;

// Middleware to parse JSON
app.use(express.json());

// Hardcoded login data
const loginDatabase = {
  1: { userId: 1, name: 'john', email: 'user1@example.com', status: 'active' },
  2: { userId: 2, name: 'user2', email: 'user2@example.com', status: 'inactive' },
  3: { userId: 3, name: 'user3', email: 'user3@example.com', status: 'inactive' }
};

// GET - Get all logins
app.get('/login', (req, res) => {
  res.status(200).json({
    success: true,
    message: 'All logins retrieved successfully',
    data: Object.values(loginDatabase),
    count: Object.keys(loginDatabase).length
  });
});

// GET - Get a specific login by ID
app.get('/login/:id', (req, res) => {
  const { id } = req.params;
  const login = loginDatabase[id];

  if (login) {
    res.status(200).json({
      success: true,
      message: 'Login retrieved successfully',
      data: login
    });
  } else {
    res.status(404).json({
      success: false,
      message: 'Login not found',
      data: null
    });
  }
});

// POST - Create a new login
app.post('/login', (req, res) => {
  const newId = Math.max(...Object.keys(loginDatabase).map(Number)) + 1;
  const newLogin = {
    id: newId,
    username: req.body.username || `user${newId}`,
    email: req.body.email || `user${newId}@example.com`,
    status: req.body.status || 'active'
  };

  loginDatabase[newId] = newLogin;

  res.status(201).json({
    success: true,
    message: 'Login created successfully',
    data: newLogin
  });
});

// PUT - Update a login
app.put('/login/:id', (req, res) => {
  const { id } = req.params;
  const login = loginDatabase[id];

  if (login) {
    const updatedLogin = {
      id: parseInt(id),
      username: req.body.username || login.username,
      email: req.body.email || login.email,
      status: req.body.status || login.status
    };

    loginDatabase[id] = updatedLogin;

    res.status(200).json({
      success: true,
      message: 'Login updated successfully',
      data: updatedLogin
    });
  } else {
    res.status(404).json({
      success: false,
      message: 'Login not found',
      data: null
    });
  }
});

// DELETE - Delete a login
app.delete('/login/:id', (req, res) => {
  const { id } = req.params;
  const login = loginDatabase[id];

  if (login) {
    const deletedLogin = loginDatabase[id];
    delete loginDatabase[id];

    res.status(200).json({
      success: true,
      message: 'Login deleted successfully',
      data: deletedLogin
    });
  } else {
    res.status(404).json({
      success: false,
      message: 'Login not found',
      data: null
    });
  }
});

// Default route
app.get('/', (req, res) => {
  res.status(200).json({
    success: true,
    message: 'Server is running',
    version: '1.0.0'
  });
});

// Start the server
app.listen(PORT, () => {
  console.log(`Server is running on http://localhost:${PORT}`);
});
