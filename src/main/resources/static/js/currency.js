/**
 * SmartBank — Indian Rupee (INR / ₹) Currency Formatting Utility
 * Uses the Indian locale (en-IN) and Indian numbering system (Lakhs and Crores grouping).
 */
function formatINR(amount) {
    if (amount === null || amount === undefined || isNaN(amount)) {
        return '₹0.00';
    }
    return new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    }).format(amount);
}

function formatINRWithoutSymbol(amount) {
    if (amount === null || amount === undefined || isNaN(amount)) {
        return '0.00';
    }
    return new Intl.NumberFormat('en-IN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    }).format(amount);
}
